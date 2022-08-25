plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

android {
    namespace = "com.leovp.jpeg"

    defaultConfig {
        // Specific your ndk.abiFilters in your project, not here. So that it will include the proper abiFilters automatically.
        externalNativeBuild {
            cmake {
                cppFlags += setOf("")
            }
            ndk {
                // abiFilters "arm64-v8a", "armeabi-v7a", "x86", "x86_64"
                abiFilters += setOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    sourceSets {
        getByName("main").jniLibs.srcDirs("src/main/libs")
    }

    externalNativeBuild {
        cmake {
            path = File(projectDir, "CMakeLists.txt")
        }
    }

    packagingOptions {
        jniLibs {
            pickFirsts += setOf(
                "lib/armeabi-v7a/libc++_shared.so",
                "lib/armeabi-v7a/libjpeg.so",
                "lib/armeabi-v7a/libturbojpeg.so",

                "lib/arm64-v8a/libc++_shared.so",
                "lib/arm64-v8a/libjpeg.so",
                "lib/arm64-v8a/libturbojpeg.so",
            )
        }
    }

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))
    api(libs.androidx.annotation)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "jpeg-sdk"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
