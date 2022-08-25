plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

android {
    namespace = "com.leovp.pref"

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    api(libs.androidx.core.ktx)

    implementation(projects.libJson)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "pref"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
