plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

// android {
//    publishing {
//        // Publishes "release" build variant with "release" component created by
//        // Android Gradle plugin
//        singleVariant("release")
//    }
// }

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "lib-network"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
