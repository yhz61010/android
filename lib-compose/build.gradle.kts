plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    id("maven-publish")
}

android {
    namespace = "com.leovp.compose"

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }

    // https://medium.com/androiddevelopers/5-ways-to-prepare-your-app-build-for-android-studio-flamingo-release-da34616bb946
    buildFeatures {
        compose = true
        // Generate BuildConfig.java file
        buildConfig = true
    }
}

dependencies {
    api(libs.bundles.lifecycle.simple)
    api(libs.lifecycle.runtime.compose)

    api(projects.log)
    api(projects.pref)

    api(libs.kotlin.reflect)
    compileOnly(libs.mmkv)
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
                artifactId = "lib-compose"
                version = libs.versions.leo.version.get()

                artifact(sourceJar.get())
                from(components["release"])
            }
        }
    }
}
