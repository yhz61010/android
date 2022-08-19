plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.kotlin.parcelize)
    `maven-publish`
}

android {
    namespace = "com.leovp.aidl_client"

    defaultConfig {
        applicationId = namespace
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
    }

    val debugSigning = signingConfigs.create("debugSigning") {
        storeFile = File(System.getenv("KEYSTORE") ?: "../debug.keystore")
        keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
        keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
        storePassword = System.getenv("KEY_PASSWORD") ?: "android"

        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
        enableV4Signing = true
    }

    val releaseSigning = signingConfigs.create("releaseSigning") {
        storeFile = File(System.getenv("KEYSTORE") ?: "../debug.keystore")
        keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
        keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
        storePassword = System.getenv("KEY_PASSWORD") ?: "android"

        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
        enableV4Signing = true
    }

    buildTypes {
        getByName("debug") {
            signingConfig = debugSigning
        }

        getByName("release") {
            signingConfig = releaseSigning
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = true
        disable += setOf(
            "MissingTranslation",
            "RtlHardcoded",
            "RtlCompat",
            "RtlEnabled"
        )
    }
}

dependencies {
    implementation(libs.bundles.androidx.simple)
    implementation(libs.android.material)
    implementation(libs.androidx.multidex)

    implementation(projects.androidbaseSdk)
    implementation(projects.libCommonAndroid)
    implementation(projects.logSdk)
}
