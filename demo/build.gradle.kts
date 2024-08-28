import android.annotation.SuppressLint
import java.io.ByteArrayOutputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.navigation)

    // Add ksp only if you use ksp() in dependencies {}
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.junit5)

    alias(libs.plugins.sonarqube)
    jacoco
}

val localProperties: Properties by rootProject.extra

android {
    namespace = "com.leovp.demo"

    defaultConfig {
        versionCode = 21
        versionName = "2.1"
        multiDexEnabled = true

        ndk {
            // abiFilters "arm64-v8a", "armeabi-v7a", "x86", "x86_64"
            @SuppressLint("ChromeOsAbiSupport")
            abiFilters += setOf("arm64-v8a")
        }

        // You can use this property in AndroidManifest as meta-data.
        manifestPlaceholders += mapOf(
            "LEO_CUSTOM_KEY" to localProperties.getProperty("leo.custom.key", "")
        )

        // Connect JUnit 5 to the runner
        testInstrumentationRunnerArguments["runnerBuilder"] = "de.mannodermaus.junit5.AndroidJUnit5Builder"
    }

    // https://developer.android.com/studio/build/build-variants
    flavorDimensions += listOf("default")

    // https://medium.com/androiddevelopers/5-ways-to-prepare-your-app-build-for-android-studio-flamingo-release-da34616bb946
    buildFeatures {
        dataBinding = true
        // viewBinding is enabled by default. Check [build.gradle.kts] in the root folder of project.
        // viewBinding = true
        aidl = true
        // Generate BuildConfig.java file
        buildConfig = true
    }

    val releaseSigning = signingConfigs.create("releaseSigning") {
        storeFile = File(System.getenv("KEYSTORE") ?: "${projectDir.absolutePath}/../debug.keystore")
        keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
        keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
        storePassword = System.getenv("KEY_PASSWORD") ?: "android"

        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
        enableV4Signing = true
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = releaseSigning
        }

        //        getByName("release") {
        //            signingConfig = releaseSigning
        //        }
    }

    productFlavors {
        create("dev") {
            // Assigns this product flavor to the "version" flavor dimension.
            // If you are using only one dimension, this property is optional,
            // and the plugin automatically assigns all the module's flavors to
            // that dimension.
            dimension = "default"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }

        //        create("prod") {
        //            dimension = "version"
        // //            applicationIdSuffix = ".prod"
        // //            versionNameSuffix = "-prod"
        //        }
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
        resources {
            excludes += setOf("META-INF/atomicfu.kotlin_module")
            pickFirsts += setOf(
                "META-INF/NOTICE",
                "META-INF/NOTICE.md",
                "META-INF/DEPENDENCIES",
                "META-INF/DEPENDENCIES.md",
                "META-INF/LICENSE",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE.txt",
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
                "META-INF/native-image/windows-x86_64/jnijavacpp/reflect-config.json",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }

    /*
    * DSL element "dexOptions" is obsolete and should be removed.
    * It will be removed in version 8.0 of the Android Gradle plugin.
    * Using it has no effect, and the AndroidGradle plugin optimizes dexing automatically.
    */
    //    dexOptions {
    //        jumboMode = true
    //        javaMaxHeapSize = "4g"
    //    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .mapNotNull { it as? com.android.build.gradle.internal.api.ApkVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = "LeoDemo${("-$flavorName").takeIf { it != "-" } ?: ""}-${buildType.name}" +
                    "-v$versionName($versionCode)" +
                    "-${gitVersionTag()}-${gitCommitCount()}" +
                    ".apk"
            }
    }

    sourceSets {
        getByName("main").jniLibs.srcDirs("src/main/libs")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = true
        disable += setOf(
            "MissingTranslation",
            "RtlHardcoded",
            "RtlCompat",
            "RtlEnabled"
        )
    }
}

// 获取当前分支的提交总次数
fun gitCommitCount(): Int {
    //    val cmd = 'git rev-list HEAD --first-parent --count'
    val cmd = "git rev-list HEAD --count"

    val stdout = ByteArrayOutputStream()
    runCatching {
        exec {
            commandLine = cmd.trim().split(' ')
            standardOutput = stdout
        }
    }.getOrDefault(0)
    // You must trim() the result. Because the result of command has a suffix '\n'.
    return stdout.toString().trim().toInt()
}

// 使用commit的哈希值作为版本号也是可以的，获取最新的一次提交的哈希值的前七个字符
// $ git rev-list HEAD --abbrev-commit --max-count=1
// a935b078

/*
 * 获取最新的一个tag信息
 * $ git describe --tags
 * 4.0.4-9-ga935b078
 * 说明：
 * 4.0.4        : tag名
 * 9            : 打tag之后又有四次提交
 * ga935b078    ：开头 g 为 git 的缩写，在多种管理工具并存的环境中很有用处
 * a935b078     ：当前分支最新的 commitID 前几位
 */

fun gitVersionTag(): String {
    // https://stackoverflow.com/a/4916591/1685062
    //    val cmd = "git describe --tags"
    val cmd = "git describe --always"

    val stdout = ByteArrayOutputStream()
    runCatching {
        exec {
            commandLine = cmd.trim().split(' ')
            standardOutput = stdout
        }
    }.getOrDefault(null) ?: return "NA"
    var versionTag = stdout.toString().trim()

    val regex = "-(\\d+)-g".toRegex()
    val matcher: MatchResult? = regex.matchEntire(versionTag)

    val matcherGroup0: MatchGroup? = matcher?.groups?.get(0)
    versionTag = if (matcher?.value?.isNotBlank() == true && matcherGroup0?.value?.isNotBlank() == true) {
        versionTag.substring(0, matcherGroup0.range.first) + "." + matcherGroup0.value
    } else {
        versionTag
    }

    return versionTag
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))

    implementation(libs.bundles.androidx.full)
    implementation(libs.android.material)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.multidex)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.bundles.lifecycle.simple)
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    implementation(projects.androidbaseSdk)
    implementation(projects.logSdk)
    implementation(projects.httpSdk)
    implementation(projects.audioSdk)
    implementation(projects.prefSdk)
    implementation(projects.libJson)
    implementation(projects.basenettySdk)
    implementation(projects.camera2liveSdk)
    implementation(projects.screencaptureSdk)
    implementation(projects.drawOnScreenSdk)
    implementation(projects.floatviewSdk)
    implementation(projects.circleProgressbarSdk)
    implementation(projects.openglSdk)
    implementation(projects.cameraxSdk)
    implementation(projects.jpegSdk)
    implementation(projects.libNetwork)
    api(projects.libCommonAndroid)
    api(projects.libCommonKotlin)
    implementation(projects.logSdk)
    implementation(projects.libBytes)
    implementation(projects.libImage)
    implementation(projects.libReflection)

    // You can enable either [ffmpeg-sdk] or [ffmpeg-javacpp-sdk]
    // implementation(projects.ffmpegSdk)
    // implementation(projects.ffmpegJavacppSdk)

    // You can enable only one of the following three modules.
    // implementation(projects.adpcmImaQtCodecSdk)
    // implementation(projects.h264HevcDecoderSdk)
    implementation(projects.adpcmImaQtCodecH264HevcDecoderSdk)

    implementation(libs.glide)
    ksp(libs.glide.compiler)

    implementation(libs.bundles.java.mail)
    implementation(libs.mars.xlog)
    implementation(libs.mmkv)
    implementation(libs.material.dialogs)
    implementation(libs.eventbus)
    implementation(libs.karn.notify)
    implementation(libs.android.animations) {
        artifact {
            type = "aar"
        }
    }
    implementation(libs.xx.permissions)
    // Koin main features for Android
    implementation(libs.koin)
    implementation(libs.bundles.bson)
    implementation(libs.free.reflection)

    // Net - dependencies - Start
    implementation(libs.bundles.kotlin.coroutines)
    implementation(libs.square.okhttp)
    implementation(libs.net)
    // Net - dependencies - End

    testRuntimeOnly(libs.bundles.test.runtime.only)
    androidTestImplementation(libs.bundles.test)
    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.bundles.powermock)
    // ==============================
    // The instrumentation test companion libraries
    androidTestImplementation(libs.mannodermaus.junit5.core)
    androidTestRuntimeOnly(libs.mannodermaus.junit5.runner)
    // ==============================
}
