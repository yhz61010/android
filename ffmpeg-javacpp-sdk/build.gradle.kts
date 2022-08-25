plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

android {
    namespace = "com.leovp.ffmpeg.javacpp"

    defaultConfig {
        ndk {
            // abiFilters "arm64-v8a", "armeabi-v7a", "x86", "x86_64"
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    sourceSets {
        getByName("main").jniLibs.srcDirs("src/main/libs")
    }

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    api(libs.androidx.core.ktx)

    // "group:name:version:classifier@extension"
    // https://mvnrepository.com/artifact/org.bytedeco/ffmpeg
    implementation(libs.bytedeco.ffmpeg) {
        artifact {
            classifier = "android-arm"
        }
    }
    implementation(libs.bytedeco.ffmpeg)

    // "group:name:version:classifier@extension"
    // https://mvnrepository.com/artifact/org.bytedeco/javacpp
    implementation(libs.bytedeco.javacpp) {
        artifact {
            classifier = "android-arm"
        }
    }
    implementation(libs.bytedeco.javacpp)

    // https://mvnrepository.com/artifact/org.bytedeco/ffmpeg
    // https://mvnrepository.com/artifact/org.bytedeco/ffmpeg-platform
    // api group: 'org.bytedeco', name: 'ffmpeg-platform', version: "$ffmpegVersion"
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "ffmpeg-javacpp"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
