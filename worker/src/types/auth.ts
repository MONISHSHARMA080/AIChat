import type { JWTPayload } from "jose";

export interface FirebaseClaimInfo {
	identities: Record<string, string[]>;
	sign_in_provider: string;
	sign_in_second_factor?: string;
	second_factor_identifier?: string;
	tenant?: string;
	[key: string]: unknown;
}

export interface FirebaseIdToken extends JWTPayload {
	iss: string;
	aud: string;
	sub: string;
	uid: string;
	user_id?: string;
	iat: number;
	exp: number;
	auth_time: number;
	email?: string;
	email_verified?: boolean;
	name?: string;
	picture?: string;
	phone_number?: string;
	firebase: FirebaseClaimInfo;
	[claim: string]: unknown;
}
