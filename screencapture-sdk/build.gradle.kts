plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.sonarqube)
    jacoco
    id("maven-publish")
}

android {
    namespace = "com.leovp.screencapture"

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))
    api(libs.androidx.annotation)
    api(libs.kotlin.coroutines.core)
    api(libs.androidx.appcompat)

    implementation(projects.logSdk)
    implementation(projects.libBytes)
    implementation(projects.libImage)
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
        // name: Module name
        create<MavenPublication>(name) {
            groupId = mavenGroupId
            artifactId = "screencapture"
            version = libs.versions.leo.version.get()

            artifact(sourceJar.get())
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
