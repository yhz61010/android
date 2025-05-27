plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.sonarqube)
    jacoco
    id("maven-publish")
}

android {
    namespace = "com.leovp.ffmpeg.javacpp"

    defaultConfig {
        ndk {
            // abiFilters "arm64-v8a", "armeabi-v7a", "x86", "x86_64"
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
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

    packaging {
        resources {
            pickFirsts += setOf(
                "META-INF/native-image/linux-x86/jnijavacpp/jni-config.json",
                "META-INF/native-image/linux-x86/jnijavacpp/reflect-config.json",
                "META-INF/native-image/linux-x86_64/jnijavacpp/jni-config.json",
                "META-INF/native-image/linux-x86_64/jnijavacpp/reflect-config.json",
                "META-INF/native-image/linux-ppc64le/jnijavacpp/jni-config.json",
                "META-INF/native-image/linux-ppc64le/jnijavacpp/reflect-config.json",
                "META-INF/native-image/linux-arm64/jnijavacpp/jni-config.json",
                "META-INF/native-image/linux-arm64/jnijavacpp/reflect-config.json",
                "META-INF/native-image/linux-armhf/jnijavacpp/jni-config.json",
                "META-INF/native-image/linux-armhf/jnijavacpp/reflect-config.json",
                "META-INF/native-image/android-arm/jnijavacpp/jni-config.json",
                "META-INF/native-image/android-arm/jnijavacpp/reflect-config.json",
                "META-INF/native-image/android-arm64/jnijavacpp/jni-config.json",
                "META-INF/native-image/android-arm64/jnijavacpp/reflect-config.json",
                "META-INF/native-image/android-x86/jnijavacpp/jni-config.json",
                "META-INF/native-image/android-x86/jnijavacpp/reflect-config.json",
                "META-INF/native-image/android-x86_64/jnijavacpp/jni-config.json",
                "META-INF/native-image/android-x86_64/jnijavacpp/reflect-config.json",
                "META-INF/native-image/macosx-x86_64/jnijavacpp/jni-config.json",
                "META-INF/native-image/macosx-x86_64/jnijavacpp/reflect-config.json",
                "META-INF/native-image/windows-x86/jnijavacpp/jni-config.json",
                "META-INF/native-image/windows-x86/jnijavacpp/reflect-config.json",
                "META-INF/native-image/windows-x86_64/jnijavacpp/jni-config.json",
                "META-INF/native-image/windows-x86_64/jnijavacpp/reflect-config.json"
            )
        }
    }
}

dependencies {
    api(libs.androidx.core.ktx)

    // "group:name:version:classifier@extension"
    // https://mvnrepository.com/artifact/org.bytedeco/ffmpeg
    implementation(libs.bytedeco.ffmpeg) {
        artifact {
            classifier = "android-arm64"
        }
    }
    implementation(libs.bytedeco.ffmpeg)

    // "group:name:version:classifier@extension"
    // https://mvnrepository.com/artifact/org.bytedeco/javacpp
    implementation(libs.bytedeco.javacpp) {
        artifact {
            classifier = "android-arm64"
        }
    }
    implementation(libs.bytedeco.javacpp)

    // https://mvnrepository.com/artifact/org.bytedeco/ffmpeg
    // https://mvnrepository.com/artifact/org.bytedeco/ffmpeg-platform
    // api group: 'org.bytedeco', name: 'ffmpeg-platform', version: "$ffmpegVersion"
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
        val customGroup: String by rootProject.extra
        // Creates a Maven publication called "release".
        create<MavenPublication>("release") {
            groupId = customGroup
            artifactId = "ffmpeg-javacpp"
            version = libs.versions.leo.version.get()

            artifact(sourceJar.get())
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
