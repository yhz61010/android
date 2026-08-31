plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    id("maven-publish")
}

android {
    namespace = "com.leovp.ffmpeg"

    sourceSets {
        getByName("main").jniLibs.directories.add("src/main/libs")
    }

    packaging {
        jniLibs {
            // Prevent stripping debug symbols from these libraries to avoid build warnings
            keepDebugSymbols += "**/*.so"
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
    compileOnly(libs.androidx.annotation)
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
                artifactId = "adpcm-ima-qt-codec-h264-hevc-decoder"

                artifact(sourceJar.get())
                from(components["release"])
            }
        }
    }
}
