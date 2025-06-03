plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.sonarqube)
    jacoco
    id("maven-publish")
}

android {
    namespace = "com.leovp.camera2live"

    // https://medium.com/androiddevelopers/5-ways-to-prepare-your-app-build-for-android-studio-flamingo-release-da34616bb946
    buildFeatures {
        // Generate BuildConfig.java file
        buildConfig = true
    }

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))

    api(libs.androidx.appcompat)

    api(projects.androidbase)
    api(projects.yuv)
    api(projects.libExif)

    // ===== The following dependencies are needed in [androidbase-sdk] module =====
    //    api project(':log-sdk')
    //    api project(':lib-json')
    //    api project(':lib-bytes')
    //    api project(':lib-image')
    //    api project(':lib-common-android')
    //    api project(':lib-common-kotlin')
    // ==============================
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

afterEvaluate {
    publishing {
        publications {
            val mavenGroupId: String by rootProject.extra
            // Creates a Maven publication called "release".
            // name: Module name
            create<MavenPublication>("release") {
                groupId = mavenGroupId
                artifactId = "camera2live"
                version = libs.versions.leo.version.get()

                artifact(sourceJar.get())
                from(components["release"])
            }
        }
    }
}
