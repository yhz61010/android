// import java.time.ZonedDateTime
// import java.time.format.DateTimeFormatter
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

// val kotlinApiDemoVersion by extra {
//     // org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
//     org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion(libs.versions.kotlin.apidemo.get())
// }
/** The property references local.properties file */
val localProperties: Properties by rootProject.extra

junitPlatform {
    // Disable the plugin's built-in Jacoco integration, which is deprecated from AGP 9.0.0 onwards.
    jacocoOptions {
        taskGenerationEnabled.set(false)
    }
}

android {
    namespace = "com.leovp.demo"

    defaultConfig {
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()

        ndk {
            // abiFilters "arm64-v8a", "armeabi-v7a", "x86", "x86_64"
            @Suppress("ChromeOsAbiSupport")
            abiFilters += setOf("armeabi-v7a", "arm64-v8a")
        }

        // You can use this property in AndroidManifest as meta-data.
        manifestPlaceholders += mapOf(
            // Get properties from local.properties file.
            "LEO_CUSTOM_KEY" to localProperties.getProperty("leo.custom.key", "")
        )

        // Connect JUnit 5 to the runner
        testInstrumentationRunnerArguments["runnerBuilder"] = "de.mannodermaus.junit5.AndroidJUnit5Builder"
    }

    // https://developer.android.com/studio/build/build-variants
    flavorDimensions += listOf("default")

    // https://medium.com/androiddevelopers/5-ways-to-prepare-your-app-build-for-android-studio-flamingo-release-da34616bb946
    buildFeatures {
        //noinspection DataBindingWithoutKapt
        dataBinding = true
        // viewBinding is enabled by default. Check [build.gradle.kts] in the root folder of project.
        // viewBinding = true
        aidl = true
        // Generate BuildConfig.java file
        buildConfig = true
    }

    signingConfigs {
        named("debug") {
            storeFile = File(rootDir, getDebugSignProperty("storeFile"))
            storePassword = getDebugSignProperty("storePassword")
            keyAlias = getDebugSignProperty("keyAlias")
            keyPassword = getDebugSignProperty("keyPassword")

            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }

        // Comment out release signing config to avoid build failure on JitPack (no keystore env vars).
        // create("release") {
        //     storeFile = System.getenv("KEYSTORE_PATH")?.let { file(it) }
        //         ?: rootProject.properties["leovp.storeFile"]?.let { file(it) }
        //     storePassword = System.getenv("KEYSTORE_PASSWORD")
        //         ?: rootProject.properties["leovp.storePassword"] as? String
        //     keyAlias = System.getenv("KEY_ALIAS")
        //         ?: rootProject.properties["leovp.keyAlias"] as? String
        //     keyPassword = System.getenv("KEY_PASSWORD")
        //         ?: rootProject.properties["leovp.keyPassword"] as? String
        //
        //     enableV1Signing = true
        //     enableV2Signing = true
        //     enableV3Signing = true
        //     enableV4Signing = true
        // }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }

        // release {
        //     signingConfig = signingConfigs.getByName("release")
        // }
        // getByName("debug") {
        //     isMinifyEnabled = false
        //     isShrinkResources = false
        //     signingConfig = signingConfigs.getByName("debug")
        // }

        // getByName("release") {
        //     signingConfig = signingConfigs.getByName("release")
        // }
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
            // Prevent stripping debug symbols from these libraries to avoid build warnings
            keepDebugSymbols += "**/*.so"
            // Prevent stripping debug symbols from these libraries to avoid build warnings
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/atomicfu.kotlin_module",
                "META-INF/androidx.emoji2_emoji2.version"
            )
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

    sourceSets {
        getByName("main").jniLibs.directories.add("src/main/libs")
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

    // This configuration will override the global setting which is configured in root build.gradle.kts.
    // https://kotlinlang.org/docs/gradle-compiler-options.html#target-the-jvm
    // tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    //     compilerOptions {
    //         apiVersion.set(kotlinApiDemoVersion)
    //     }
    // }
}

// Comment out APK renaming to avoid build failure on JitPack (git commands not available).
// AGP 9.0 removed outputFileName from VariantOutput API.
// Use Copy task with SingleArtifact.APK to customize APK naming.
// androidComponents {
//     onVariants { variant ->
//         // Example: debug
//         val buildTypeName = variant.buildType ?: "unknown"
//         // Example: DevDebug
//         val capitalizedName = variant.name.replaceFirstChar { it.uppercase() }
//         // Example: dev
//         val flavorName = variant.productFlavors.firstOrNull()?.second.orEmpty()
//         println("buildTypeName=$buildTypeName flavorName=$flavorName capitalizedName=$capitalizedName")
//         val versionName = android.defaultConfig.versionName ?: "NA"
//         val versionCode = android.defaultConfig.versionCode ?: 0
//         val timestamp = ZonedDateTime.now()
//             .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss(z)"))
//
//         // Use providers to defer Git calls
//         val commitCount = gitCommitCount()
//         val versionTag = gitVersionTag()
//
//         tasks.register("rename${capitalizedName}Apk") {
//             val apkDir = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK)
//             inputs.dir(apkDir)
//             doLast {
//                 val apkName = "LeoDemo${("-$flavorName").takeIf { it != "-" } ?: ""}-$buildTypeName" +
//                     "-v$versionName($versionCode)" +
//                     "-${timestamp}" +
//                     "-${versionTag.get()}-${commitCount.get()}" +
//                     ".apk"
//                 val dir = apkDir.get().asFile
//                 dir.listFiles()?.filter { it.extension == "apk" }?.forEach { srcFile ->
//                     val finalName = if ("unsigned" in srcFile.name) {
//                         apkName.replace(".apk", "-unsigned.apk")
//                     } else {
//                         apkName
//                     }
//                     srcFile.copyTo(File(dir, finalName), overwrite = true)
//                 }
//             }
//         }
//         afterEvaluate {
//             tasks.named("assemble${capitalizedName}") {
//                 finalizedBy("rename${capitalizedName}Apk")
//             }
//         }
//     }
// }

fun Project.getDebugSignProperty(
    key: String,
    path: String = "10-configs/sign/keystore.properties",
): String =
    Properties()
        .apply {
            rootProject.file(path).inputStream().use(::load)
        }.getProperty(key)

// // Get the total commit count of the current branch.
// fun gitCommitCount(): Provider<String> {
//     // val cmd = 'git rev-list HEAD --first-parent --count'
//     return providers.exec {
//         commandLine = listOf("git", "rev-list", "HEAD", "--count")
//     }.standardOutput.asText.map { it.trim() }.orElse("NA")
// }
//
// // You can also use the commit hash as a version number.
// // Get the first 7 characters of the latest commit hash:
// // $ git rev-list HEAD --abbrev-commit --max-count=1
// // a935b078
//
// /*
//  * Get the latest tag info.
//  * $ git describe --tags
//  * 4.0.4-9-ga935b078
//  * Explanation:
//  * 4.0.4        : tag name
//  * 9            : 9 commits after the tag
//  * ga935b078    : 'g' stands for git, useful in multi-VCS environments
//  * a935b078     : first few characters of the latest commitID on the current branch
//  */
// fun gitVersionTag(): Provider<String> {
//     // https://stackoverflow.com/a/4916591/1685062
//     //    val cmd = "git describe --tags"
//     val rawTag = providers.exec {
//         commandLine = listOf("git", "describe", "--always")
//     }.standardOutput.asText.map { it.trim() }.orElse("NA")
//
//     return rawTag.map { versionTag ->
//         val regex = "-(\\d+)-g".toRegex()
//         val matcher: MatchResult? = regex.matchEntire(versionTag)
//         val matcherGroup0: MatchGroup? = matcher?.groups?.get(0)
//         if (matcher?.value?.isNotBlank() == true && matcherGroup0?.value?.isNotBlank() == true) {
//             versionTag.take(matcherGroup0.range.first) + "." + matcherGroup0.value
//         } else {
//             versionTag
//         }
//     }
// }

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))

    implementation(libs.bundles.androidx.full)
    implementation(libs.android.material)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.bundles.lifecycle.simple)
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    implementation(projects.androidbase)
    implementation(projects.log)
    implementation(projects.http)
    implementation(projects.audio)
    implementation(projects.pref)
    implementation(projects.libJson)
    implementation(projects.basenetty)
    implementation(projects.camera2live)
    implementation(projects.screencapture)
    implementation(projects.drawOnScreen)
    implementation(projects.floatview)
    implementation(projects.circleProgressbar)
    implementation(projects.opengl)
    implementation(projects.camerax)
    implementation(projects.jpeg)
    implementation(projects.libNetwork)
    api(projects.libCommonAndroid)
    api(projects.libCommonKotlin)
    implementation(projects.log)
    implementation(projects.libBytes)
    implementation(projects.libImage)
    implementation(projects.libReflection)
    implementation(projects.nfc)

    // You can enable either [ffmpeg-sdk] or [ffmpeg-javacpp-sdk]
    // implementation(projects.ffmpegSdk)
    // implementation(projects.ffmpegJavacppSdk)

    // You can enable only one of the following three modules.
    // implementation(projects.adpcmImaQtCodecSdk)
    // implementation(projects.h264HevcDecoderSdk)
    implementation(projects.adpcmImaQtCodecH264HevcDecoder)

    implementation(libs.glide)
    ksp(libs.glide.ksp)

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
