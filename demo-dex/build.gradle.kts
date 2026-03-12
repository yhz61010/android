plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.leovp.dexdemo"

    defaultConfig {
        versionCode = 1
        versionName = "1.0"
    }

    // https://medium.com/androiddevelopers/5-ways-to-prepare-your-app-build-for-android-studio-flamingo-release-da34616bb946
    buildFeatures {
        // Generate BuildConfig.java file
        buildConfig = true
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

    packaging {
        resources {
            excludes += setOf("META-INF/androidx.emoji2_emoji2.version")
        }
    }
    // or
    // packagingOptions.resources.excludes += setOf(
    //     "META-INF/androidx.emoji2_emoji2.version"
    // )

}

// AGP 9.0 removed outputFileName from VariantOutput API.
// Use Copy task with SingleArtifact.APK to customize APK naming.
androidComponents {
    onVariants { variant ->
        val capitalizedName = variant.name.replaceFirstChar { it.uppercase() }

        tasks.register<Copy>("rename${capitalizedName}Apk") {
            from(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK))
            into(layout.buildDirectory.dir("outputs/renamed-apk/${variant.name}"))
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
            include("*.apk")
            rename { _ -> "dexdemo.dex" }
        }
        afterEvaluate {
            tasks.named("assemble${capitalizedName}") {
                finalizedBy("rename${capitalizedName}Apk")
            }
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))

    implementation(libs.androidx.appcompat)
    implementation(libs.android.material)
    implementation(libs.androidasync)

    implementation(projects.dex)
}
