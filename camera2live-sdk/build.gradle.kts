plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

android {
    namespace = "com.leovp.camera2live"

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))

    api(libs.androidx.appcompat)

    api(projects.androidbaseSdk)
    api(projects.yuvSdk)
    api(projects.libExif)

    // ===== The following dependencies are needed in [androidbase-sdk] module =====
    //    api project(':log-sdk')
    //    api project(':lib-json')
    //    api project(':lib-bytes')
    //    api project(':lib-image')
    //    api project(':lib-common-android')
    //    api project(':lib-common-kotlin')
    // ==============================
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "camera2live-sdk"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
