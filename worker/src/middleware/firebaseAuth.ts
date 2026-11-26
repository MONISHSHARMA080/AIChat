import { createRemoteJWKSet, errors, jwtVerify } from "jose";
import {
	AUTH_CLOCK_SKEW_SECONDS,
	FIREBASE_JWKS_URL,
	firebaseAuthConfigSchema,
	JWKS_REFRESH_COOLDOWN_MS,
	parseEnv,
	REQUEST_TIMEOUT_MS,
} from "../config/constants.ts";
import type { FirebaseIdToken } from "../types/auth.ts";
import { AppError } from "../types/errors.ts";

const firebaseJwks = createRemoteJWKSet(new URL(FIREBASE_JWKS_URL), {
	timeoutDuration: REQUEST_TIMEOUT_MS,
	cooldownDuration: JWKS_REFRESH_COOLDOWN_MS,
});

function isJwksFetchError(error: unknown): boolean {
	if (!(error instanceof errors.JOSEError)) {
		return true;
	}
	return (
		error instanceof errors.JWKSTimeout ||
		error instanceof errors.JWKSInvalid ||
		error instanceof errors.JWKInvalid ||
		error.code === "ERR_JOSE_GENERIC"
	);
}

async function verifyFirebaseRequest(
	request: Request,
	firebaseProjectId: string,
): Promise<FirebaseIdToken> {
	const authorizationHeader = request.headers.get("Authorization")?.trim() ?? "";
	if (authorizationHeader.slice(0, 7).toLowerCase() !== "bearer ") {
		throw new AppError("unauthenticated", "sign in required");
	}
	const token = authorizationHeader.slice(7).trim();
	if (!token) {
		throw new AppError("unauthenticated", "sign in required");
	}

	let payload: FirebaseIdToken;
	let kid: unknown;
	try {
		const verified = await jwtVerify<FirebaseIdToken>(token, firebaseJwks, {
			algorithms: ["RS256"],
			audience: firebaseProjectId,
			issuer: `https://securetoken.google.com/${firebaseProjectId}`,
			requiredClaims: ["exp", "iat", "sub", "auth_time"],
		});
		payload = verified.payload;
		kid = verified.protectedHeader.kid;
	} catch (error) {
		if (isJwksFetchError(error)) {
			throw new AppError("unavailable", "could not load auth signing keys");
		}
		throw new AppError("unauthenticated", "invalid auth token");
	}

	if (typeof kid !== "string" || kid.trim().length === 0) {
		throw new AppError("unauthenticated", "invalid auth token key id");
	}

	const nowSeconds = Math.floor(Date.now() / 1000);
	const subject = typeof payload.sub === "string" ? payload.sub.trim() : "";
	if (!subject || subject.length > 128) {
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

	return {
		...payload,
		uid: subject,
	};
}

export async function withFirebaseAuth(
	request: Request,
	env: Env,
	next: (user: FirebaseIdToken) => Promise<Response>,
): Promise<Response> {
	const { FIREBASE_PROJECT_ID } = parseEnv(firebaseAuthConfigSchema, env);
	const user = await verifyFirebaseRequest(request, FIREBASE_PROJECT_ID);
	return next(user);
}
