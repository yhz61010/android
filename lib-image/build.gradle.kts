plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    id("maven-publish")
}

android {
    ndkVersion = libs.versions.ndk.sdk.get()

    namespace = "com.leovp.image"

    defaultConfig {
        // Specific your ndk.abiFilters in your project, not here. So that it will include the proper abiFilters automatically.
        @Suppress("UnstableApiUsage")
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
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
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
        val mavenGroupId: String by rootProject.extra
        // Creates a Maven publication called "release".
        create<MavenPublication>("release") {
            groupId = mavenGroupId
            artifactId = "lib-image"
            version = libs.versions.leo.version.get()

            artifact(sourceJar.get())
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
