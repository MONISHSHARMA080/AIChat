package com.agentic.aichat.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.NoCredentialException
import com.agentic.aichat.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneMultiFactorInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GoogleSignInResult {
	data class Success(val user: AuthUser) : GoogleSignInResult
	data object Cancelled : GoogleSignInResult
	data object NoAccounts : GoogleSignInResult
	data object Interrupted : GoogleSignInResult
	data class Failed(val cause: Throwable) : GoogleSignInResult
}

@Singleton
class AuthRepository @Inject constructor(
	@ApplicationContext private val context: Context,
) {
	suspend fun signInWithGoogle(activity: Activity): GoogleSignInResult {
		val idToken = try {
			fetchIdToken(activity)
		} catch (error: Exception) {
			 return when (error){
				 is GetCredentialCancellationException -> GoogleSignInResult.Cancelled
				 is NoCredentialException -> GoogleSignInResult.NoAccounts
				 is GetCredentialInterruptedException -> GoogleSignInResult.Interrupted
				 else -> GoogleSignInResult.Failed(error)
			 }
		}
		return firebaseSignIn(idToken)
	}

	private suspend fun fetchIdToken(activity: Activity): String {
		if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
			throw IllegalStateException("Missing Google web client ID")
		}
		val option = GetGoogleIdOption.Builder()
			.setFilterByAuthorizedAccounts(false)
			.setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
			.build()
		val request = GetCredentialRequest.Builder()
			.addCredentialOption(option)
			.build()
		val result = CredentialManager.create(context).getCredential(activity, request)
		return GoogleIdTokenCredential.createFrom(result.credential.data).idToken
	}

	private suspend fun firebaseSignIn(idToken: String): GoogleSignInResult =
		withContext(Dispatchers.IO) {
			val auth = FirebaseAuth.getInstance()
			val outcome = runCatching {
				val credential = GoogleAuthProvider.getCredential(idToken, null)
				Tasks.await(auth.signInWithCredential(credential))
			}
			val user = outcome.getOrNull()?.user?.toAuthUser()
			if (user != null) {
				GoogleSignInResult.Success(user)
			} else {
				GoogleSignInResult.Failed(outcome.exceptionOrNull() ?: Exception("Empty Firebase user"))
			}
		}

	private fun FirebaseUser.toAuthUser() = AuthUser(
		uid = uid,
		providerId = providerId,
		displayName = displayName,
		email = email,
		phoneNumber = phoneNumber,
		photoUrl = photoUrl?.toString(),
		isAnonymous = isAnonymous,
		isEmailVerified = isEmailVerified,
		tenantId = tenantId,
		creationTimestamp = metadata?.creationTimestamp,
		lastSignInTimestamp = metadata?.lastSignInTimestamp,
//		providerData = providerData.map { info ->
//			AuthProviderData(
//				providerId = info.providerId,
//				uid = info.uid,
//				displayName = info.displayName,
//				email = info.email,
//				phoneNumber = info.phoneNumber,
//				photoUrl = info.photoUrl?.toString(),
//				isEmailVerified = info.isEmailVerified,
//			)
//		},
//		enrolledFactors = multiFactor.enrolledFactors.map { factor ->
//			AuthFactorInfo(
//				factorId = factor.factorId,
//				uid = factor.uid,
//				displayName = factor.displayName,
//				enrollmentTimestamp = factor.enrollmentTimestamp,
//				phoneNumber = (factor as? PhoneMultiFactorInfo)?.phoneNumber,
//			)
//		},
	)
}
