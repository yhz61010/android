plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.sonarqube)
    jacoco
    id("maven-publish")
}

android {
    namespace = "com.leovp.opengl"

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    api(libs.androidx.annotation)

    implementation(projects.log)
    implementation(projects.libCommonAndroid)
    implementation(projects.libCommonKotlin)
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
                artifactId = "opengl"
                version = libs.versions.leo.version.get()

                artifact(sourceJar.get())
                from(components["release"])
            }
        }
    }
}
