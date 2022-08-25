plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.navigation)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

android {
    namespace = "com.leovp.camerax"

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    api(libs.androidx.appcompat)

    api(libs.android.material)
    api(libs.kotlin.coroutines)
    api(libs.androidx.concurrent.futures.ktx)
    api(libs.androidx.constraintlayout)
    // Lifecycles only (without ViewModel or LiveData)
    api(libs.lifecycle.runtime)

    // Navigation library
    api(libs.androidx.navigation.fragment)
    api(libs.androidx.navigation.ui)

    // CameraX core library
    api(libs.bundles.camerax)

    api(projects.libCommonAndroid)
    api(projects.libCommonKotlin)
    api(projects.libImage)
    api(projects.libExif)
    api(projects.logSdk)
    api(projects.prefSdk)

    api(libs.coil)
    api(libs.coil.video)

    api(libs.xx.permissions)
    api(libs.subsampling.scale.image.view)

    // This is the enhanced version of [subsampling-scale-image-view-androidx]
    //    implementation "com.github.piasy:BigImageViewer:$bigImageViewerVersion"
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "camerax"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
