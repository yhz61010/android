plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.sonarqube)
    jacoco
    id("maven-publish")
}

android {
    namespace = "com.leovp.androidbase"

    // https://medium.com/androiddevelopers/5-ways-to-prepare-your-app-build-for-android-studio-flamingo-release-da34616bb946
    buildFeatures {
        // Generate BuildConfig.java file
        buildConfig = true
    }

    defaultConfig {
        multiDexEnabled = true
    }

    lint {
        abortOnError = false
    }

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))

    api(libs.kotlin.coroutines.core)
    api(libs.androidx.multidex)

    api(libs.bundles.lifecycle.full)
    api(libs.android.material)
    api(libs.androidx.core.ktx)
    api(libs.androidx.preference)
    api(libs.androidx.activity)
    api(libs.androidx.fragment)

    androidTestImplementation(libs.bundles.android.test)
    testImplementation(libs.bundles.powermock)
    testImplementation(libs.bundles.test)

    api(projects.log)
    api(projects.libJson)
    api(projects.libBytes)
    api(projects.libImage)
    api(projects.libCommonAndroid)
    api(projects.libCommonKotlin)
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
                artifactId = "androidbase"
                version = libs.versions.leo.version.get()

                artifact(sourceJar.get())
                from(components["release"])
            }
        }
    }
}
