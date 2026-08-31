plugins {
    id("com.android.application")
}

android {
    namespace = "com.leovp.ffmpeg.runtime.test"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.leovp.ffmpeg.runtime.test"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

val ffmpegModules =
    providers.gradleProperty("ffmpegModules")
        .map { value -> value.split(',').filter(String::isNotBlank) }
        .getOrElse(emptyList())
val ffmpegVersion = providers.gradleProperty("ffmpegVersion").get()

dependencies {
    ffmpegModules.forEach { module ->
        implementation("com.leovp.android:$module:$ffmpegVersion")
    }
}
