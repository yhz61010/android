plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    id("maven-publish")
}

android {
    ndkVersion = libs.versions.ndk.sdk.get()

    namespace = "com.leovp.jpeg"

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
        getByName("main").jniLibs.srcDirs("src/main/libs")
    }

    externalNativeBuild {
        cmake {
            path = File(projectDir, "CMakeLists.txt")
        }
    }

    packaging {
        jniLibs {
            pickFirsts += setOf(
                "lib/armeabi-v7a/libc++_shared.so",
                "lib/armeabi-v7a/libjpeg.so",
                "lib/armeabi-v7a/libturbojpeg.so",

                "lib/arm64-v8a/libc++_shared.so",
                "lib/arm64-v8a/libjpeg.so",
                "lib/arm64-v8a/libturbojpeg.so"
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

/** When use it: sourceJar.get() */
val sourceJar by tasks.registering(Jar::class) {
    from(android.sourceSets["main"].java.srcDirs)
    archiveClassifier.set("sources")
}

/** When use it: tasks.getByName("sourcesJar") */
// tasks.register<Jar>("sourcesJar") {
//     from(android.sourceSets["main"].java.srcDirs)
//     archiveClassifier.set("sources")
// }

publishing {
    publications {
        val customGroup: String by rootProject.extra
        // Creates a Maven publication called "release".
        create<MavenPublication>("jpeg-sdk") {
            groupId = customGroup
            artifactId = "jpeg"
            version = libs.versions.leo.version.get()

            artifact(sourceJar.get())
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
