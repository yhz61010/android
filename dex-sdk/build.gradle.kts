plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    id("maven-publish")
}

android {
    namespace = "com.leovp.dex"

    buildFeatures {
        aidl = true
    }

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    api(libs.free.reflection)
    api(projects.libReflection)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "dex"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
