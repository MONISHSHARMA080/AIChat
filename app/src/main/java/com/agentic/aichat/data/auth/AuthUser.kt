package com.agentic.aichat.data.auth

data class AuthUser(
	val uid: String,
	val providerId: String,
	val displayName: String?,
	val email: String?,
	val phoneNumber: String?,
	val photoUrl: String?,
	val isAnonymous: Boolean,
	val isEmailVerified: Boolean,
	val tenantId: String?,
	val creationTimestamp: Long?,
	val lastSignInTimestamp: Long?,
//	val providerData: List<AuthProviderData>,
//	val enrolledFactors: List<AuthFactorInfo>,
)

data class AuthProviderData(
	val providerId: String,
	val uid: String,
	val displayName: String?,
	val email: String?,
	val phoneNumber: String?,
	val photoUrl: String?,
	val isEmailVerified: Boolean,
)

//data class AuthFactorInfo(
//	val factorId: String,
//	val uid: String,
//	val displayName: String?,
//	val enrollmentTimestamp: Long,
//	val phoneNumber: String?,
//)
