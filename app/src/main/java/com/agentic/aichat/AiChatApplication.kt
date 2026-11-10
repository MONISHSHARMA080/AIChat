package com.agentic.aichat

import android.app.Application
import android.content.pm.ApplicationInfo
import com.agentic.aichat.billing.RevenueCatConfig
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AiChatApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		if (isDebuggable()) {
			Purchases.logLevel = LogLevel.DEBUG
		}
		Purchases.configure(
			PurchasesConfiguration.Builder(this, RevenueCatConfig.PUBLIC_SDK_KEY).build()
		)
	}

	private fun isDebuggable(): Boolean {
		return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
	}
}
