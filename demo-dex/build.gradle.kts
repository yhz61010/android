plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.leovp.dexdemo"

    // https://medium.com/androiddevelopers/5-ways-to-prepare-your-app-build-for-android-studio-flamingo-release-da34616bb946
    @Suppress ("UnstableApiUsage")
    buildFeatures {
        // Generate BuildConfig.java file
        buildConfig = true
    }

    defaultConfig {
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        // Due to dex project, we can't obfuscate the `release` build type.
        getByName("release") {
            isShrinkResources = false
            isMinifyEnabled = false
        }

        getByName("debug") {
            isShrinkResources = false
            isMinifyEnabled = false
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .mapNotNull { it as? com.android.build.gradle.internal.api.ApkVariantOutputImpl }
            .forEach { output ->
                variant.packageApplicationProvider.get().outputDirectory
                output.outputFileName = "dexdemo.dex"
            }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))

    implementation(libs.androidx.appcompat)
    implementation(libs.android.material)
    implementation(libs.androidasync)

    implementation(projects.dexSdk)
}
