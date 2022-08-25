plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.sonarqube)
    jacoco
    `maven-publish`
}

android {
    namespace = "com.leovp.basenetty"

    publishing {
        // Publishes "release" build variant with "release" component created by
        // Android Gradle plugin
        singleVariant("release")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))
    // api(libs.netty.all)
    // api(libs.netty.handler)
    // https://mvnrepository.com/artifact/io.netty/netty-codec-http
    api(libs.netty.codec.http)

    api(libs.kotlin.coroutines)

    // No need to use this library when adding pipeline however we indeed need its library.
    // So what's the magic?
    // https://mvnrepository.com/artifact/com.jcraft/jzlib
    api(libs.jzlib)

    api(projects.logSdk)
    api(projects.libBytes)
    api(projects.libNetwork)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "${rootProject.group}"
                artifactId = "basenetty"
                version = libs.versions.leo.version.get()
            }
        }
    }
}
