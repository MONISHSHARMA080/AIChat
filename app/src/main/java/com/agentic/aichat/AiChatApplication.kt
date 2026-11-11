package com.agentic.aichat

import android.app.Application
import android.content.pm.ApplicationInfo
import com.agentic.aichat.billing.RevenueCatConfig
import com.agentic.aichat.util.log
import com.google.firebase.auth.FirebaseAuth
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AiChatApplication : Application() {
	@Inject
	lateinit var firebaseAuth: FirebaseAuth

	override fun onCreate() {
		super.onCreate()
		if (isDebuggable()) {
			Purchases.logLevel = LogLevel.DEBUG
		}
		Purchases.configure(
			PurchasesConfiguration.Builder(this, RevenueCatConfig.PUBLIC_SDK_KEY).build()
		)
		firebaseAuth.addAuthStateListener { auth ->
			syncRevenueCatUser(auth.currentUser?.uid)
		}
	}

	private fun syncRevenueCatUser(uid: String?) {
		if (uid == null) {
			Purchases.sharedInstance.logOut(object : ReceiveCustomerInfoCallback {
				override fun onReceived(customerInfo: CustomerInfo) {
					log("RevenueCat logout completed")
				}

				override fun onError(error: PurchasesError) {
					log("RevenueCat logout failed: ${error.message}")
				}
			})
			return
		}
		if (Purchases.sharedInstance.appUserID == uid) return
		Purchases.sharedInstance.logIn(uid, object : LogInCallback {
			override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
				log("RevenueCat login completed created=$created")
			}

			override fun onError(error: PurchasesError) {
				log("RevenueCat login failed: ${error.message} and error:$error")
			}
		})
	}

	private fun isDebuggable(): Boolean {
		return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
	}
}
