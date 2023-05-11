rootProject.name = "LeoAndroidBaseUtil"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    resolutionStrategy {
        eachPlugin {
            //            println("id=${requested.id.id} version=${requested.version} namespace=${requested.id.namespace}")
            //            when (requested.id.id) {
            //                "com.android.application",
            //                "com.android.library",
            //                "com.android.dynamic-feature" -> {
            //                    val agpCoordinates: String by settings
            //                    useModule(agpCoordinates)
            //                }
            //                "androidx.navigation.safeargs.kotlin" -> {
            //                    val navigationCoordinates: String by settings
            //                    useModule(navigationCoordinates)
            //                }
            //                "de.mannodermaus.android-junit5" -> {
            //                    val androidJnit5Coordinates: String by settings
            //                    useModule(androidJnit5Coordinates) // navigationVersion
            //                }
            //            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/public")
        maven("https://jitpack.io")
        maven("https://maven.java.net/content/groups/public/")
    }
}

// https://docs.gradle.org/7.0/release-notes.html
// Type-safe project accessors
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    "log-sdk",
    "pref-sdk",
    "http-sdk",
    "floatview-sdk",
    "demo",
    "demo-dex",
    "screencapture-sdk",
    "basenetty-sdk",
    "androidbase-sdk",
    "camera2live-sdk",
    "camerax-sdk",
    "audio-sdk",
    "adpcm-ima-qt-codec-sdk",
    "h264-hevc-decoder-sdk",
    "adpcm-ima-qt-codec-h264-hevc-decoder-sdk",
    "draw-on-screen-sdk",
    "aidl-client",
    "ffmpeg-javacpp-sdk",
    "circle-progressbar-sdk",
    "dex-sdk",
    "yuv-sdk",
    "jpeg-sdk",
    "opengl-sdk",

    "lib-bytes",
    "lib-json",
    "lib-compress",
    "lib-image",
    "lib-exif",
    "lib-network",
    "lib-reflection",
    "lib-common-android",
    "lib-common-kotlin",

    // "ffmpeg-sdk"
)
