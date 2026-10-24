package com.agentic.aichat.billing

/**
 * Central place for RevenueCat configuration values.
 */
object RevenueCatConfig {
	// TODO: Replace with the app-specific public SDK key from RevenueCat
	//  (Project Settings > API keys > App specific keys, the "Google Play" key).
	//  Never commit a real key here for production builds.
	const val PUBLIC_SDK_KEY: String = "REPLACEME_WITH_REVENUECAT_PUBLIC_SDK_KEY"

	// Entitlement used to gate Pro features ("pro" is the RevenueCat convention).
	const val ENTITLEMENT_ID: String = "pro"
}
