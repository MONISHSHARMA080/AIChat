import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.ksp)
	alias(libs.plugins.hilt)
	alias(libs.plugins.google.services)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
	localPropertiesFile.inputStream().use { localProperties.load(it) }
}
fun buildConfigValue(localKey: String, envKey: String): String =
	localProperties.getProperty(localKey) ?: System.getenv(envKey) ?: ""

val googleWebClientId: String = buildConfigValue("google.web.client.id", "GOOGLE_WEB_CLIENT_ID")
val revenueCatPublicSdkKeyDebug: String = buildConfigValue("revenuecat.public.sdk.key", "REVENUECAT_PUBLIC_SDK_KEY")
val revenueCatPublicSdkKeyRelease: String = System.getenv("REVENUECAT_PUBLIC_SDK_KEY") ?: ""

android {
	namespace = "com.agentic.aichat"
	compileSdk {
		version = release(37)
	}

	defaultConfig {
		applicationId = "com.agentic.aichat"
		minSdk = 35
		targetSdk = 37
		versionCode = 1
		versionName = "1.0"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
		buildConfigField("String", "REVENUECAT_PUBLIC_SDK_KEY", "\"$revenueCatPublicSdkKeyDebug\"")
	}

	buildTypes {
		debug {
			buildConfigField(
				"String",
				"FUNCTIONS_BASE_URL",
				"\"http://10.0.2.2:5001/aichat-8d4f3/us-central1/\"",
			)
			buildConfigField(
				"String",
				"FIREBASE_AUTH_EMULATOR_HOST",
				"\"10.0.2.2\"",
			)
			buildConfigField(
				"int",
				"FIREBASE_AUTH_EMULATOR_PORT",
				"9099",
			)
			buildConfigField(
				"String",
				"FUNCTIONS_EMULATOR_HOST",
				"\"10.0.2.2\"",
			)
			buildConfigField(
				"int",
				"FUNCTIONS_EMULATOR_PORT",
				"5001",
			)
			buildConfigField("String", "REVENUECAT_PUBLIC_SDK_KEY", "\"$revenueCatPublicSdkKeyDebug\"")
		}
		release {
			buildConfigField(
				"String",
				"FUNCTIONS_BASE_URL",
				"\"https://us-central1-aichat-8d4f3.cloudfunctions.net/\"",
			)
			buildConfigField(
				"String",
				"FIREBASE_AUTH_EMULATOR_HOST",
				"\"\"",
			)
			buildConfigField(
				"int",
				"FIREBASE_AUTH_EMULATOR_PORT",
				"9099",
			)
			buildConfigField(
				"String",
				"FUNCTIONS_EMULATOR_HOST",
				"\"\"",
			)
			buildConfigField(
				"int",
				"FUNCTIONS_EMULATOR_PORT",
				"5001",
			)
			buildConfigField("String", "REVENUECAT_PUBLIC_SDK_KEY", "\"$revenueCatPublicSdkKeyRelease\"")
			optimization {
				enable = false
			}
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	buildFeatures {
		compose = true
		buildConfig = true
	}
}

dependencies {
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
	implementation(libs.androidx.compose.material.icons.core)
	implementation(libs.androidx.compose.material.icons.extended)
	implementation(libs.androidx.contentpager)
	implementation(libs.androidx.navigation3.runtime)
	implementation(libs.androidx.navigation3.ui)
	implementation(libs.androidx.room3.runtime)
	implementation(libs.revenuecat.purchases)
	implementation(libs.androidx.play.review)
	implementation(libs.androidx.play.review.ktx)
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.hilt.android)
	ksp(libs.hilt.android.compiler)
	implementation(libs.androidx.hilt.navigation.compose)
	ksp(libs.androidx.room3.compiler)
	implementation(platform(libs.firebase.bom))
	implementation(libs.firebase.auth)
	implementation(libs.firebase.functions)
	implementation(libs.androidx.credentials)
	implementation(libs.androidx.credentials.play.services)
	implementation(libs.googleid)
	implementation(libs.retrofit)
	implementation(libs.retrofit.kotlinx.serialization.converter)
	implementation(libs.okhttp)
	implementation(libs.okhttp.logging.interceptor)
	implementation(libs.coil3.compose)
	implementation(libs.coil3.network.okhttp)
	implementation(libs.coil3.svg)
	implementation(libs.kotlinx.serialization.json)
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.kotlinx.coroutines.reactive)
	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.test)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(libs.androidx.junit)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
	debugImplementation(libs.androidx.compose.ui.tooling)
}