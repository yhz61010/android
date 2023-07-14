plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

android {
    namespace = "com.leovp.audio"

    // https://medium.com/androiddevelopers/5-ways-to-prepare-your-app-build-for-android-studio-flamingo-release-da34616bb946
    @Suppress ("UnstableApiUsage")
    buildFeatures {
        // Generate BuildConfig.java file
        buildConfig = true
    }

    @Suppress ("UnstableApiUsage")
    sourceSets {
        getByName("main").jniLibs.srcDirs("src/main/libs")
    }

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))

    api(libs.kotlin.coroutines)
    api(libs.androidx.annotation)

    api(projects.logSdk)
    api(projects.libBytes)
    api(projects.libCompress)
    api(projects.libCommonKotlin)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "audio"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
