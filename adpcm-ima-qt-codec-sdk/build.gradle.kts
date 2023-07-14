plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    `maven-publish`
}

android {
    namespace = "com.leovp.ffmpeg.audio"

    @Suppress ("UnstableApiUsage")
    sourceSets {
        getByName("main").jniLibs.srcDirs("src/main/libs")
    }

    packaging {
        jniLibs {
            pickFirsts += setOf(
                "lib/armeabi-v7a/libavutil.so",
                "lib/armeabi-v7a/libavcodec.so",
                "lib/armeabi-v7a/libswresample.so",
                "lib/armeabi-v7a/libavfilter.so",
                "lib/armeabi-v7a/libavdevice.so",
                "lib/armeabi-v7a/libavformat.so",
                "lib/armeabi-v7a/libswscale.so",
                "lib/armeabi-v7a/libc++_shared.so",
                "lib/armeabi-v7a/libadpcm-ima-qt-encoder.so",
                "lib/armeabi-v7a/libadpcm-ima-qt-decoder.so",
                "lib/armeabi-v7a/libh264-hevc-decoder.so",

                "lib/arm64-v8a/libavutil.so",
                "lib/arm64-v8a/libavcodec.so",
                "lib/arm64-v8a/libswresample.so",
                "lib/arm64-v8a/libavfilter.so",
                "lib/arm64-v8a/libavdevice.so",
                "lib/arm64-v8a/libavformat.so",
                "lib/arm64-v8a/libswscale.so",
                "lib/arm64-v8a/libc++_shared.so",
                "lib/arm64-v8a/libadpcm-ima-qt-encoder.so",
                "lib/arm64-v8a/libadpcm-ima-qt-decoder.so",
                "lib/arm64-v8a/libh264-hevc-decoder.so"
            )
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
    implementation(libs.androidx.annotation)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "adpcm-ima-qt-codec"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
