plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    id("maven-publish")
}

android {
    namespace = "com.leovp.reflection"

    buildFeatures {
        aidl = true
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
    api(libs.free.reflection)

    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.bundles.test)
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
            artifactId = "lib-reflection"
            version = libs.versions.leo.version.get()

            artifact(sourceJar.get())
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
