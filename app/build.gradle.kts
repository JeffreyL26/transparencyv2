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
        versionCode = 5
        versionName = "1.0.2"

        // AdMob-App-ID ins Manifest (Pflicht-Meta-data, sonst Crash beim Start).
        manifestPlaceholders["admobAppId"] = admobAppId
        // Ad-Unit-IDs als BuildConfig-Felder (Test-Defaults).
        buildConfigField("String", "ADMOB_NATIVE_UNIT_ID", "\"$admobNativeUnitId\"")
        buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$admobRewardedUnitId\"")
    }

    // Play-Sprach-Splits aus: sonst liefert das Bundle nur die values-*-Ordner der
    // Systemsprachen aus und der In-App-Sprachwechsler fällt auf values/ (Deutsch)
    // zurück. Bei reinen String-Ressourcen ist der Größenzuwachs vernachlässigbar.
    bundle {
        language {
            enableSplit = false
        }
    }

    buildTypes {
        release {
            // R8: Play bemängelt fehlende Verschleierung ohne Minify. Keep-Regeln
            // für die dadurch riskanten Stellen (Room, kotlinx.serialization) stehen
            // mit Begründung in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
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

// Release-Guard (CLAUDE.md §9.2): Bricht jeden Release-Build ab, sobald eine der
// AdMob-IDs noch Googles Test-Publisher-ID enthält. Grund: adProp() fällt still
// auf die Test-IDs zurück, wenn die Keys in local.properties fehlen — ein so
// gebautes Bundle liefert im Store nur Test-Anzeigen aus. Debug-Builds und der
// Gradle-Sync sind nicht betroffen (Prüfung erst bei Ausführung von preReleaseBuild).
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        val testPublisher = "3940256099942544"
        val offenders = listOf(
            "ADMOB_APP_ID" to admobAppId,
            "ADMOB_NATIVE_UNIT_ID" to admobNativeUnitId,
            "ADMOB_REWARDED_UNIT_ID" to admobRewardedUnitId,
        ).filter { (_, value) -> value.contains(testPublisher) }.map { it.first }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Release-Build abgebrochen: AdMob-Test-IDs ($testPublisher) aktiv für " +
                    "${offenders.joinToString(", ")}.\n" +
                    "Erwartete Keys in local.properties (oder als Gradle-Property): " +
                    "ADMOB_APP_ID, ADMOB_NATIVE_UNIT_ID, ADMOB_REWARDED_UNIT_ID " +
                    "(Format ADMOB_APP_ID=ca-app-pub-…~…, Unit-IDs ca-app-pub-…/…; " +
                    "keine Anführungszeichen, keine Leerzeichen um '=', keine BOM). " +
                    "Siehe CLAUDE.md §9.2 und scripts/check-admob-ids.sh."
            )
        }
    }
}
