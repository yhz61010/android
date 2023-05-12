plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

android {
    ndkVersion = libs.versions.ndk.sdk.get()

    namespace = "com.leovp.image"

    defaultConfig {
        // Specific your ndk.abiFilters in your project, not here. So that it will include the proper abiFilters automatically.
        externalNativeBuild {
            cmake {
                cppFlags += setOf("")
            }
            ndk {
                // abiFilters "arm64-v8a", "armeabi-v7a", "x86", "x86_64"
                abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            }
        }
    }

    sourceSets {
        getByName("main").jniLibs.srcDirs("libs")
    }

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    api(libs.androidx.annotation)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "lib-image"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
