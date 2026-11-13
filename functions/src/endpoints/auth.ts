import { logger } from "firebase-functions";
import { onCall, HttpsError } from "firebase-functions/https";
import { TURSO_AUTH_TOKEN, TURSO_DATABASE_URL } from "../config";
import { upsertUser, type UserRow } from "../db/turso";

type TokenClaims = {
  email?: string;
  name?: string;
  picture?: string;
  phone_number?: string;
  firebase?: { sign_in_provider?: string };
  email_verified?: boolean;
  auth_time?: number;
  tenant_id?: string;
};

function toUserResponse(row: UserRow) {
  return {
    uid: row.uid,
    email: row.email,
    displayName: row.display_name,
    photoUrl: row.photo_url,
    phoneNumber: row.phone_number,
    provider: row.provider,
    emailVerified: (row.email_verified ?? 0) === 1,
    isAnonymous: (row.is_anonymous ?? 0) === 1,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    lastSeenAt: row.last_seen_at,
  };
}

export const syncUser = onCall({ secrets: [TURSO_DATABASE_URL, TURSO_AUTH_TOKEN] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "sign in required");
  const uid = request.auth.uid;
  const claims = request.auth.token as TokenClaims;
  const provider = claims.firebase?.sign_in_provider ?? "unknown";
  const now = Date.now();
  try {
    const row = await upsertUser({
      uid,
      email: claims.email ?? null,
      displayName: claims.name ?? null,
      photoUrl: claims.picture ?? null,
      phoneNumber: claims.phone_number ?? null,
      provider,
      emailVerified: claims.email_verified ?? false,
      isAnonymous: provider === "anonymous",
      tenantId: claims.tenant_id ?? null,
      createdAt: now,
      updatedAt: now,
      lastSeenAt: typeof claims.auth_time === "number" ? claims.auth_time * 1000 : now,
    });
    return toUserResponse(row);
  } catch (error) {
    logger.error("syncUser failed", error);
    throw new HttpsError("internal", "could not sync user");
  }
});
