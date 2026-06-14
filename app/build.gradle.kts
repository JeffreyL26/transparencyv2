import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// AdMob-IDs werden NICHT im Repo hartkodiert (CLAUDE.md §9.2): aus local.properties
// (gitignored) oder Gradle-Property gelesen, sonst Googles offizielle TEST-IDs.
// Echte IDs erst kurz vor Release setzen — Klicks auf echte Ads im Test = Sperrgefahr.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun adProp(key: String, testDefault: String): String =
    localProps.getProperty(key) ?: providers.gradleProperty(key).orNull ?: testDefault

val admobAppId = adProp("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
val admobNativeUnitId = adProp("ADMOB_NATIVE_UNIT_ID", "ca-app-pub-3940256099942544/2247696110")
val admobRewardedUnitId = adProp("ADMOB_REWARDED_UNIT_ID", "ca-app-pub-3940256099942544/5224354917")

android {
    namespace = "com.jbateam.scanconvert"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jbateam.scanconvert"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // AdMob-App-ID ins Manifest (Pflicht-Meta-data, sonst Crash beim Start).
        manifestPlaceholders["admobAppId"] = admobAppId
        // Ad-Unit-IDs als BuildConfig-Felder (Test-Defaults).
        buildConfigField("String", "ADMOB_NATIVE_UNIT_ID", "\"$admobNativeUnitId\"")
        buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$admobRewardedUnitId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.camera.mlkit.vision)
    implementation(libs.mlkit.text.recognition)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Monetarisierung: Play Billing 8, AdMob (native + rewarded), UMP-Consent.
    implementation(libs.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
}
