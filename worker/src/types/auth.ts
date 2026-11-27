export interface AuthUser {
	uid: string;
	email: string | null;
	displayName: string | null;
	photoUrl: string | null;
	phoneNumber: string | null;
	provider: string;
	emailVerified: boolean;
	isAnonymous: boolean;
	tenantId: string | null;
	authTimeMs: number;
}

export interface SyncedUser extends Omit<AuthUser, "tenantId" | "authTimeMs"> {
	createdAt: number;
	updatedAt: number;
	lastSeenAt: number;
}
