plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

android {
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

    api(libs.kotlin.coroutines)
    api(libs.androidx.multidex)

    api(libs.bundles.lifecycle.full)
    api(libs.android.material)
    api(libs.androidx.core.ktx)
    api(libs.androidx.preference)
    api(libs.androidx.activity)
    api(libs.androidx.fragment)

    api(libs.eventbus)

    androidTestImplementation(libs.bundles.android.test)
    testImplementation(libs.bundles.powermock)
    testImplementation(libs.bundles.test)

    api(projects.logSdk)
    api(projects.libJson)
    api(projects.libBytes)
    api(projects.libImage)
    api(projects.libCommonAndroid)
    api(projects.libCommonKotlin)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "androidbase-sdk"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
