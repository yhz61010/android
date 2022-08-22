plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

android {
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
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "audio-sdk"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
