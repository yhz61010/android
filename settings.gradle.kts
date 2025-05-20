rootProject.name = "LeoAndroidBaseUtil"

// https://developer.android.com/studio/build?hl=zh-cn#settings-file
pluginManagement {
    /**
     * The pluginManagement {repositories {...}} block configures the
     * repositories Gradle uses to search or download the Gradle plugins and
     * their transitive dependencies. Gradle pre-configures support for remote
     * repositories such as JCenter, Maven Central, and Ivy. You can also use
     * local repositories or define your own remote repositories. The code below
     * defines the Gradle Plugin Portal, Google's Maven repository,
     * and the Maven Central Repository as the repositories Gradle should use to
     * look for its dependencies.
     */
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        // Tencent Gradle mirrors
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugins/") }
        maven { url = uri("https://mirrors.tuna.tsinghua.edu.cn/maven") }
        // AliYun Gradle mirrors
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }

        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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
            //                    val androidJunit5Coordinates: String by settings
            //                    useModule(androidJunit5Coordinates) // navigationVersion
            //                }
            //            }
        }
    }
}

dependencyResolutionManagement {
    /**
     * The dependencyResolutionManagement {repositories {...}}
     * block is where you configure the repositories and dependencies used by
     * all modules in your project, such as libraries that you are using to
     * create your application. However, you should configure module-specific
     * dependencies in each module-level build.gradle file. For new projects,
     * Android Studio includes Google's Maven repository and the
     * Maven Central Repository by default,
     * but it does not configure any dependencies (unless you select a
     * template that requires some).
     */
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    @Suppress("UnstableApiUsage")
    repositories {
        maven("https://jitpack.io")

        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")

        maven("https://mirrors.cloud.tencent.com/gradle/")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")

        maven("https://plugins.gradle.org/m2/")
        maven("https://maven.java.net/content/groups/public/")
        // https://github.com/airbnb/lottie/blob/master/android-compose.md
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }

        google()
        mavenCentral {
            isAllowInsecureProtocol = true
        }
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
    "lib-common-kotlin"

    // "ffmpeg-sdk"
)
