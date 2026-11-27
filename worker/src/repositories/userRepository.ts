import type { AuthUser, SyncedUser } from "../types/auth.ts";

interface UserRow {
	uid: string;
	email: string | null;
	display_name: string | null;
	photo_url: string | null;
	phone_number: string | null;
	provider: string;
	email_verified: number | null;
	is_anonymous: number | null;
	created_at: number;
	updated_at: number;
	last_seen_at: number;
}

const USER_COLUMNS =
	"uid, email, display_name, photo_url, phone_number, provider, email_verified, is_anonymous, tenant_id, created_at, updated_at, last_seen_at";

const UPSERT_USER_SQL = `INSERT INTO users (${USER_COLUMNS}) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uid) DO UPDATE SET email = excluded.email, display_name = excluded.display_name, photo_url = excluded.photo_url, phone_number = excluded.phone_number, provider = excluded.provider, email_verified = excluded.email_verified, is_anonymous = excluded.is_anonymous, tenant_id = excluded.tenant_id, updated_at = excluded.updated_at, last_seen_at = excluded.last_seen_at RETURNING ${USER_COLUMNS}`;

const TOUCH_USER_SQL = `INSERT INTO users (${USER_COLUMNS}) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uid) DO UPDATE SET last_seen_at = MAX(users.last_seen_at, excluded.last_seen_at)`;

function userParams(
	user: AuthUser,
	createdAt: number,
	updatedAt: number,
	lastSeenAt: number,
): unknown[] {
	return [
		user.uid,
		user.email,
		user.displayName,
		user.photoUrl,
		user.phoneNumber,
		user.provider,
		user.emailVerified ? 1 : 0,
		user.isAnonymous ? 1 : 0,
		user.tenantId,
		createdAt,
		updatedAt,
		lastSeenAt,
	];
}

export function prepareTouchUserStatement(
	db: D1Database,
	user: AuthUser,
	at: number,
): D1PreparedStatement {
	return db.prepare(TOUCH_USER_SQL).bind(...userParams(user, at, at, at));
}

export async function upsertUser(db: D1Database, user: AuthUser): Promise<SyncedUser> {
	const now = Date.now();
	const row = await db
		.prepare(UPSERT_USER_SQL)
		.bind(...userParams(user, now, now, user.authTimeMs))
		.first<UserRow>();
	if (!row) {
		throw new Error("upsertUser did not return a row");
	}
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
