import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// ---------------------------------------------------------------------------
// Versioning overrides for release builds. `tools/build-local-aab.sh` passes
// -PandroidVersionCode / -PandroidVersionName so every Google Play upload gets a
// unique, increasing version code. Local builds fall back to the committed
// defaults below.
// ---------------------------------------------------------------------------
val overrideVersionCode = providers.gradleProperty("androidVersionCode").orNull?.toIntOrNull()
val overrideVersionName = providers.gradleProperty("androidVersionName").orNull

require(overrideVersionCode == null || overrideVersionCode in 1..2_100_000_000) {
    "androidVersionCode must be between 1 and 2,100,000,000 (was $overrideVersionCode)"
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // Without this Kotlin/Native guesses the bundle id from package names.
            binaryOption("bundleId", "com.grace.app.ComposeApp")
        }

        // RevenueCat KMP ships compiled Swift shims. The app framework links fine (a
        // static framework tolerates unresolved symbols), but a *test executable* must
        // resolve them, so link the Swift runtime compatibility libraries the same way
        // Xcode would. See docs/monetization-setup.md.
        iosTarget.compilations.getByName("test").compileTaskProvider.configure {
            compilerOptions {
                freeCompilerArgs.addAll(
                    swiftTestLinkerArgs(isSimulator = iosTarget.name == "iosSimulatorArm64")
                )
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.mlkit.image.labeling)
            implementation(libs.google.mobile.ads)
            implementation(libs.google.user.messaging.platform)
        }
        // The RevenueCat KMP SDK binds to StoreKit/StoreKit 2 through cinterop,
        // so the iOS source sets must opt in.
        named { it.lowercase().startsWith("ios") }.configureEach {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.coil.compose)
            implementation(libs.multiplatform.settings)
            implementation(libs.okio)
            implementation(libs.purchases.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }
    }
}

/**
 * Secrets are read from `local.properties` (gitignored) or the matching environment
 * variables — never from a build script:
 *
 * Release signing:
 *   GRACE_KEYSTORE_PATH=/abs/path/grace-release.jks
 *   GRACE_KEYSTORE_PASSWORD=...
 *   GRACE_KEY_ALIAS=...
 *   GRACE_KEY_PASSWORD=...
 *
 * Monetization (see docs/monetization-setup.md):
 *   GRACE_ADMOB_APP_ID_ANDROID=ca-app-pub-…~…
 *   GRACE_ADMOB_INTERSTITIAL_UNIT_ID_ANDROID=ca-app-pub-…/…
 *   GRACE_REVENUECAT_KEY_ANDROID=appl_…
 *
 * When the keystore values are absent the release APK is left unsigned
 * (`composeApp-release-unsigned.apk`) so a clean checkout never ships a
 * debug-signed artefact by accident.
 */
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(key: String): String? =
    (localProps.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }

val keystorePath = secret("GRACE_KEYSTORE_PATH")
val releaseSigningReady = keystorePath != null && rootProject.file(keystorePath).exists()

// Google's official sample IDs — always valid to *run* with, never valid to *ship*.
val sampleAndroidAppId = "ca-app-pub-3940256099942544~3347511713"
val sampleInterstitialUnitId = "ca-app-pub-3940256099942544/1033173712"
val revenuecatPlaceholder = "REPLACE_ME"

val productionAdMobAppId = secret("GRACE_ADMOB_APP_ID_ANDROID")
val productionInterstitialUnitId = secret("GRACE_ADMOB_INTERSTITIAL_UNIT_ID_ANDROID")
val productionRevenueCatKey = secret("GRACE_REVENUECAT_KEY_ANDROID")

/**
 * Swift runtime compatibility libraries for iOS test executables that pull in RevenueCat's
 * Swift cinterop shims. The linker opts point at the active Xcode toolchain's Swift lib
 * directory — DEVELOPER_DIR first (which the repo's iOS commands already set), then the
 * standard installs.
 */
fun swiftTestLinkerArgs(isSimulator: Boolean): List<String> {
    val subdir = if (isSimulator) "iphonesimulator" else "iphoneos"
    val devDir = System.getenv("DEVELOPER_DIR")?.takeIf { it.isNotBlank() }
    val candidates = listOfNotNull(devDir) + listOf(
        "/Applications/Xcode.app/Contents/Developer",
        "/Applications/Xcode-16.4.app/Contents/Developer",
        "/Applications/Xcode-16.3.app/Contents/Developer"
    )
    val libDir = candidates
        .map { "$it/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$subdir" }
        .firstOrNull { File(it).exists() }
        ?: return emptyList()

    return listOf(
        "-linker-option", "-L$libDir",
        "-linker-option", "-lswiftCompatibility56",
        "-linker-option", "-lswiftCompatibilityConcurrency"
    )
}

android {
    namespace = "com.grace.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.grace.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = overrideVersionCode ?: 1
        versionName = overrideVersionName ?: "1.0.4"

        // AdMob application ID consumed by AndroidManifest's ${adMobAppId} placeholder.
        manifestPlaceholders["adMobAppId"] = sampleAndroidAppId

        buildConfigField("String", "ADMOB_APP_ID", "\"$sampleAndroidAppId\"")
        buildConfigField(
            "String",
            "ADMOB_INTERSTITIAL_UNIT_ID",
            "\"$sampleInterstitialUnitId\""
        )
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenuecatPlaceholder\"")
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = rootProject.file(keystorePath!!)
                storePassword = secret("GRACE_KEYSTORE_PASSWORD")
                keyAlias = secret("GRACE_KEY_ALIAS")
                keyPassword = secret("GRACE_KEY_PASSWORD")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        getByName("debug") {
            // Google's sample IDs, explicitly — a release override must never leak in.
            manifestPlaceholders["adMobAppId"] = sampleAndroidAppId
            buildConfigField("String", "ADMOB_APP_ID", "\"$sampleAndroidAppId\"")
            buildConfigField(
                "String",
                "ADMOB_INTERSTITIAL_UNIT_ID",
                "\"$sampleInterstitialUnitId\""
            )
            buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenuecatPlaceholder\"")
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            val releaseAppId = productionAdMobAppId ?: sampleAndroidAppId
            val releaseUnitId = productionInterstitialUnitId ?: sampleInterstitialUnitId
            val releaseRcKey = productionRevenueCatKey ?: revenuecatPlaceholder

            manifestPlaceholders["adMobAppId"] = releaseAppId
            buildConfigField("String", "ADMOB_APP_ID", "\"$releaseAppId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"$releaseUnitId\"")
            buildConfigField("String", "REVENUECAT_API_KEY", "\"$releaseRcKey\"")

            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.lifecycle(
                    "Grace: no release keystore configured (GRACE_KEYSTORE_PATH) — " +
                        "the release APK will be unsigned."
                )
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * Release-only validation of the monetization identifiers.
 *
 * Attached to `preReleaseBuild` (the first task any release assembly runs). The values the
 * action needs are captured inside the task configuration block — a serializable
 * `Provider<RegularFile>` for `local.properties` and the `-P` escape flag, which Gradle
 * tracks as a configuration-cache input — so nothing references the project at execution
 * time. Debug builds are never touched, so a clean checkout stays fully buildable.
 */
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    val localPropertiesFile = project.rootProject.file("local.properties")
    val allowPlaceholder =
        project.providers.gradleProperty("allowPlaceholderMonetization").orNull == "true"

    doFirst {
        val props = Properties().apply {
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use { load(it) }
            }
        }
        fun env(key: String): String? =
            (props.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }

        val sampleAppId = "ca-app-pub-3940256099942544~3347511713"
        val sampleUnitId = "ca-app-pub-3940256099942544/1033173712"
        val placeholderRc = "REPLACE_ME"

        val appId = env("GRACE_ADMOB_APP_ID_ANDROID") ?: sampleAppId
        val unitId = env("GRACE_ADMOB_INTERSTITIAL_UNIT_ID_ANDROID") ?: sampleUnitId
        val rc = env("GRACE_REVENUECAT_KEY_ANDROID") ?: placeholderRc
        val sampleIds = appId == sampleAppId || unitId == sampleUnitId
        val missingRc = rc == placeholderRc || rc.length <= 20 || !rc.contains('_')

        if (!allowPlaceholder && (sampleIds || missingRc)) {
            throw GradleException(
                """
                |
                |Grace release build refused: monetization is still placeholder-configured.
                |
                |Set these in local.properties (gitignored) or the environment — see
                |docs/monetization-setup.md for where each value comes from:
                |
                |  GRACE_ADMOB_APP_ID_ANDROID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
                |  GRACE_ADMOB_INTERSTITIAL_UNIT_ID_ANDROID=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
                |  GRACE_REVENUECAT_KEY_ANDROID=appl_XXXXXXXXXXXXXXXX
                |
                |To build a release APK without them (local R8/shrinker verification only):
                |
                |  ./gradlew :composeApp:assembleRelease -PallowPlaceholderMonetization=true
                """.trimMargin()
            )
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.grace.app.resources"
    generateResClass = always
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}
