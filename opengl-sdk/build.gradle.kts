plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

//android {
//        publishing {
//        // Publishes "release" build variant with "release" component created by
//        // Android Gradle plugin
//        singleVariant("release")
//    }
//}

dependencies {
    api(libs.androidx.annotation)

    implementation(projects.logSdk)
    implementation(projects.libCommonAndroid)
    implementation(projects.libCommonKotlin)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "opengl-sdk"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
