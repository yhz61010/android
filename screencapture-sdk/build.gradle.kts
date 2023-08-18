plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

android {
    namespace = "com.leovp.screencapture"

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))
    api(libs.androidx.annotation)
    api(libs.kotlin.coroutines.core)
    api(libs.androidx.appcompat)

    implementation(projects.logSdk)
    implementation(projects.libBytes)
    implementation(projects.libImage)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "screencapture"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
