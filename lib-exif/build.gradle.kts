plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}


android {
//    publishing {
//        // Publishes "release" build variant with "release" component created by
//        // Android Gradle plugin
//        singleVariant("release")
//    }
}

dependencies {
    api(libs.androidx.annotation)
    // EXIF Interface
    implementation(libs.androidx.exifinterface)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "lib-exif"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
