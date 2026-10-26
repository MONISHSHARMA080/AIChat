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
val googleWebClientId: String =
	localProperties.getProperty("google.web.client.id")
		?: System.getenv("GOOGLE_WEB_CLIENT_ID")
		?: ""

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
	}

	buildTypes {
		release {
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
	implementation(libs.androidx.credentials)
	implementation(libs.androidx.credentials.play.services)
	implementation(libs.googleid)
	testImplementation(libs.junit)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(libs.androidx.junit)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
	debugImplementation(libs.androidx.compose.ui.tooling)
}