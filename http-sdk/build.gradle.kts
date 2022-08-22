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
    implementation(projects.libNetwork)
    implementation(projects.logSdk)

    api(libs.square.okhttp)
    api(libs.rxjava2.android)
    api(libs.square.retrofit2)
    api(libs.square.retrofit2.gson)
    api(libs.square.retrofit2.converter.scalars)
    api(libs.square.retrofit2.adapter.rxjava2)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "http-sdk"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
