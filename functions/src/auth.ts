import { getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { HttpsError, type CallableRequest, type Request } from "firebase-functions/v2/https";

export function ensureAdmin(): void {
  if (getApps().length === 0) {
    initializeApp();
  }
}

export function requireAuth(request: CallableRequest): string {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  return uid;
}

export function requireUidMatch(request: CallableRequest, uid: string): string {
  const authUid = requireAuth(request);
  if (!uid) {
    throw new HttpsError("invalid-argument", "uid is required.");
  }
  if (authUid !== uid) {
    throw new HttpsError("permission-denied", "uid does not match authenticated user.");
  }
  return authUid;
}

export function getAuthorizationHeader(req: Request): string {
  return req.get("Authorization") ?? req.get("authorization") ?? "";
}

export function extractBearerToken(header: string): string | null {
  const match = /^Bearer\s+(.+)$/i.exec(header.trim());
  return match ? match[1].trim() : null;
}

export interface VerifiedToken {
  uid: string;
  isAnonymous: boolean;
}

export async function verifyIdTokenClaims(req: Request): Promise<VerifiedToken | null> {
  const token = extractBearerToken(getAuthorizationHeader(req));
  if (!token) {
    return null;
  }
  try {
    ensureAdmin();
    const decoded = await getAuth().verifyIdToken(token);
    const provider = (decoded as { firebase?: { sign_in_provider?: string } }).firebase?.sign_in_provider;
    return { uid: decoded.uid, isAnonymous: provider === "anonymous" };
  } catch {
    return null;
  }
}

export async function verifyIdToken(req: Request): Promise<string | null> {
  const claims = await verifyIdTokenClaims(req);
  return claims ? claims.uid : null;
}
