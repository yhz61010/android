plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

android {
    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    api(libs.androidx.appcompat)
    api(libs.androidx.core.ktx)
    api(libs.androidx.annotation)

    api(projects.libCommonKotlin)
    implementation(projects.floatviewSdk)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "lib-common-android"
                version = libs.versions.leo.version.get()
            }
        }
    }
}