plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.leovp.demo_dex"

    defaultConfig {
        applicationId = namespace
        versionCode = 1
        versionName = "1.0"
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .mapNotNull { it as? com.android.build.gradle.internal.api.ApkVariantOutputImpl }
            .forEach { output ->
                variant.packageApplicationProvider.get().outputDirectory
                output.outputFileName = "dexdemo.dex"
            }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))

    implementation(libs.androidx.appcompat)
    implementation(libs.android.material)
    implementation(libs.androidasync)

    implementation(projects.dexSdk)
}
