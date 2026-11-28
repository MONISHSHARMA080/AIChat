import { createRemoteJWKSet, errors, jwtVerify, type JWTPayload } from "jose";
import { firebaseAuthConfigSchema, parseEnv, REQUEST_TIMEOUT_MS } from "../env.ts";
import { AppError } from "../errors.ts";
import type { AuthUser } from "../types/auth.ts";

const FIREBASE_JWKS_URL =
	"https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const JWKS_REFRESH_COOLDOWN_MS = 60_000;
const AUTH_CLOCK_SKEW_SECONDS = 60;

interface FirebaseJwtPayload extends JWTPayload {
	sub?: string;
	iat?: number;
	auth_time?: number;
	email?: string;
	email_verified?: boolean;
	name?: string;
	picture?: string;
	phone_number?: string;
	tenant_id?: string;
	firebase?: {
		sign_in_provider?: string;
		tenant?: string;
	};
}

const firebaseJwks = createRemoteJWKSet(new URL(FIREBASE_JWKS_URL), {
	timeoutDuration: REQUEST_TIMEOUT_MS,
	cooldownDuration: JWKS_REFRESH_COOLDOWN_MS,
});

function extractAuthToken(request: Request): string {
	const authorizationHeader = request.headers.get("Authorization")?.trim() ?? "";
	if (authorizationHeader.slice(0, 7).toLowerCase() === "bearer ") {
		const headerToken = authorizationHeader.slice(7).trim();
		if (headerToken) {
			return headerToken;
		}
	}
	if (request.headers.get("Upgrade")?.toLowerCase() === "websocket") {
		const queryToken = new URL(request.url).searchParams.get("token")?.trim() ?? "";
		if (queryToken) {
			return queryToken;
		}
	}
	throw new AppError("unauthenticated", "sign in required");
}

export async function withFirebaseAuth(
	request: Request,
	env: Env,
	next: (user: AuthUser) => Promise<Response>,
): Promise<Response> {
	const { FIREBASE_PROJECT_ID } = parseEnv(firebaseAuthConfigSchema, env);
	const token = extractAuthToken(request);

	let payload: FirebaseJwtPayload;
	let kid: unknown;
	try {
		const verified = await jwtVerify<FirebaseJwtPayload>(token, firebaseJwks, {
			algorithms: ["RS256"],
			audience: FIREBASE_PROJECT_ID,
			issuer: `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`,
			requiredClaims: ["exp", "iat", "sub", "auth_time"],
			clockTolerance: AUTH_CLOCK_SKEW_SECONDS,
		});
		payload = verified.payload;
		kid = verified.protectedHeader.kid;
	} catch (error) {
		const isJwksError =
			!(error instanceof errors.JOSEError) ||
			error instanceof errors.JWKSTimeout ||
			error instanceof errors.JWKSInvalid ||
			error instanceof errors.JWKInvalid ||
			error.code === "ERR_JOSE_GENERIC";
		if (isJwksError) {
			throw new AppError("unavailable", "could not load auth signing keys");
		}
		throw new AppError("unauthenticated", "invalid auth token");
	}

	if (typeof kid !== "string" || kid.trim().length === 0) {
		throw new AppError("unauthenticated", "invalid auth token key id");
	}

	const nowSeconds = Math.floor(Date.now() / 1000);
	const uid = typeof payload.sub === "string" ? payload.sub.trim() : "";
	if (!uid || uid.length > 128) {
		throw new AppError("unauthenticated", "invalid auth token subject");
	}
	if (
		typeof payload.iat !== "number" ||
		!Number.isFinite(payload.iat) ||
		payload.iat > nowSeconds + AUTH_CLOCK_SKEW_SECONDS
	) {
		throw new AppError("unauthenticated", "invalid auth token issue time");
	}
	if (
		typeof payload.auth_time !== "number" ||
		!Number.isFinite(payload.auth_time) ||
		payload.auth_time > nowSeconds + AUTH_CLOCK_SKEW_SECONDS
	) {
		throw new AppError("unauthenticated", "invalid auth token authentication time");
	}

	const provider = payload.firebase?.sign_in_provider ?? "unknown";
	const tenantId =
		payload.firebase?.tenant ?? (typeof payload.tenant_id === "string" ? payload.tenant_id : null);

	return next({
		uid,
		email: payload.email ?? null,
		displayName: payload.name ?? null,
		photoUrl: payload.picture ?? null,
		phoneNumber: payload.phone_number ?? null,
		provider,
		emailVerified: payload.email_verified === true,
		isAnonymous: provider === "anonymous",
		tenantId,
		authTimeMs: payload.auth_time * 1000,
	});
}
