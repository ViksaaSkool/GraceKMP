import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.mlkit.image.labeling)
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.grace.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.grace.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.4"
    }

    /**
     * Release signing. Credentials are read from `local.properties` (gitignored)
     * or the matching environment variables — never from this file:
     *
     *   GRACE_KEYSTORE_PATH=/abs/path/grace-release.jks
     *   GRACE_KEYSTORE_PASSWORD=...
     *   GRACE_KEY_ALIAS=...
     *   GRACE_KEY_PASSWORD=...
     *
     * When they are absent the release APK is left unsigned
     * (`composeApp-release-unsigned.apk`) so the build still works on a clean
     * checkout without shipping a debug-signed artefact by accident.
     */
    val localProps = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    fun secret(key: String): String? =
        (localProps.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }

    val keystorePath = secret("GRACE_KEYSTORE_PATH")
    val releaseSigningReady = keystorePath != null && rootProject.file(keystorePath).exists()

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
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

compose.resources {
    publicResClass = true
    packageOfResClass = "com.grace.app.resources"
    generateResClass = always
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}
