plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    id("maven-publish")
}

android {
    namespace = "com.leovp.nfc"

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    api(libs.androidx.core.ktx)
    api(projects.log)
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
                artifactId = "nfc"
                version = libs.versions.leo.version.get()

                artifact(sourceJar.get())
                from(components["release"])
            }
        }
    }
}
