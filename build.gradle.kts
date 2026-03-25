import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import io.gitlab.arturbosch.detekt.Detekt
import java.util.Locale
import java.util.Properties
import org.jlleitschuh.gradle.ktlint.KtlintExtension

// =====================================
// ========== Global settings ==========
// =====================================

val mavenGroupId: String by extra {
    "com.leovp.android"
}

/**
 * You can use it in subproject like this:
 * ```kotlin
 * val javaVersion: JavaVersion by rootProject.extra
 * ```
 */
val javaVersion: JavaVersion by extra {
    // JavaVersion.VERSION_17
    // We should use integer value for toVersion() in this case.
    JavaVersion.toVersion(libs.versions.javaVersion.get().toInt())
}
val jvmTargetVersion by extra {
    org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmVersion.get())
}
// val kotlinApiVersion by extra {
//     // org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
//     org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion(libs.versions.kotlin.api.get())
// }
// val kotlinLanguageVersion by extra {
//     // org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
//     org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion(libs.versions.kotlin.language.get())
// }

val useResourcePrefix = false

/**
 * resourcePrefix 的校验规则：
 * 1. 针对可识别类型的文件夹中的非 `values` 文件夹目录，校验 `xml` 文件的文件前缀是否符合规则。
 * 2. 针对 `values` 文件夹中的文件，不校验文件前缀，校验文件中 name 元素的值。
 * 3. 针对二进制文件，校验文件前缀。
 * 4. 图片资源通常来说也应该被检验文件前缀，但是我们通常会进行如下 lint 设置 abortOnError = false 因此错误提示被忽略了。
 *
 * **Attention:**
 * All the occurrences `-` dash in `Module Name` will be replaced with `_` underscore.
 *
 * @see <a href="https://blog.csdn.net/weixin_43910395/article/details/120166450">resourcePrefix</a>
 */

/**
 * Reads the properties in local.properties that is used in the AndroidManifest or Gradle file.
 *
 * You can use it in subproject like this:
 * val localProperties: Properties by rootProject.extra
 *
 * Don't forget to import the following package:
 * import java.util.Properties
 */
@Suppress("unused")
val localProperties: Properties by extra { gradleLocalProperties(rootProject.rootDir, providers) }

// =====================================

// https://developer.android.com/studio/build?hl=zh-cn#top-level

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    /**
     * You should use `apply false` in the top-level build.gradle file
     * to add a Gradle plugin as a build dependency, but not apply it to the
     * current (root) project. You should not use `apply false` in sub-projects.
     * For more information, see
     * Applying external plugins with same version to subprojects.
     */

    // https://docs.gradle.org/current/userguide/plugins.html#sec:subprojects_plugins_dsl
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false

    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint.gradle)
    alias(libs.plugins.benmanes.versions)
    // alias(libs.plugins.vcu)
    jacoco

    alias(libs.plugins.ksp) apply false
    // If you use kotlin(), you can change dash(-) with dot(.)
    // or you can still use dash like id("kotlin-parcelize")
    alias(libs.plugins.kotlin.parcelize) apply false // id("kotlin-parcelize")
    // https://stackoverflow.com/a/72508037/1685062
    alias(libs.plugins.navigation) apply false
}

// ********************
// https://gist.github.com/huuphuoc1396/497c27c1fac205f5d9c6696016227e1c
//
// Fix the problem: "Unexpected SMAP line: *S KotlinDebug"
// ********************
jacoco {
    toolVersion = rootProject.libs.versions.jacoco.get()
}

val detektFormatting: Provider<MinimalExternalModuleDependency> =
    libs.detekt.formatting

// all projects = root project + sub projects
allprojects {
    group = mavenGroupId

    // We want to apply ktlint at all project level because it also checks Gradle config files (*.kts)
    apply(plugin = rootProject.libs.plugins.ktlint.gradle.get().pluginId)
     configure<KtlintExtension> { version.set(rootProject.libs.versions.detekt.get()) }

    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
    // or
    // apply {
    //     plugin(rootProject.libs.plugins.detekt.get().pluginId)
    // }

    detekt {
        config.from(files("$rootDir/detekt.yml"))

        parallel = true

        // By default, detekt does not check test source set and Gradle specific files,
        // so hey have to be added manually.
        source.from(
            files(
                "$rootDir/buildSrc",
                "$rootDir/build.gradle.kts",
                "$rootDir/settings.gradle.kts",
                "src/main/kotlin",
                "src/test/kotlin",
            ),
        )
    }

    // Ktlint configuration for sub-projects
    ktlint {
        verbose.set(true)
        android.set(true)
        ignoreFailures.set(false)

        // Uncomment below line and run .\gradlew ktlintCheck to see check ktlint experimental rules
        // enableExperimentalRules.set(true)

        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        }

        filter {
            exclude { element -> element.file.path.contains("generated/") }
        }
    }

    tasks.withType<Test> {
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
        useJUnitPlatform()
    }

    // https://medium.com/kacper.wojciechowski/kotlin-2-0-android-project-migration-guide-b1234fbbff65
    // Configure it in your each compose module.
    // composeCompiler {
    //     enableStrongSkippingMode = true
    //     includeSourceInformation = true
    // }

    configureCompileTasks()

    // afterEvaluate {
    //     configureCompileTasks()
    // }

    // configurations.all {
    //     resolutionStrategy.eachDependency {
    //         println(
    //             "module=${requested.module}:${requested.version} group=${requested.group} name=${requested.name}"
    //         )
    //     }
    // }

    dependencies {
        // We must define `detektFormatting` in outside, or else the compile error will occur.
        detektPlugins(detektFormatting)
    }
}

subprojects {
    // AGP 9.0 has built-in Kotlin support, so kotlin-android plugin is no longer needed.
    // apply(
    //     plugin =
    //         rootProject.libs.plugins.kotlin.android
    //             .get()
    //             .pluginId,
    // )

    plugins.withId(rootProject.libs.plugins.android.application.get().pluginId) {
        // println("displayName=$displayName, name=$name, group=$group")
        configureApplication()
    }

    plugins.withId(rootProject.libs.plugins.android.library.get().pluginId) { configureLibrary() }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

/**
 * Configure to eliminate the following warning:
 * ```
 * 'compileReleaseJavaWithJavac' task (current target is 1.8) and 'compileReleaseKotlin'
 * task (current target is 11) jvm target compatibility should be set to the same Java version.
 * ```
 */
fun Project.configureCompileTasks() {
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = javaVersion.toString()
        targetCompatibility = javaVersion.toString()
        // Enable warning for deprecated APIs
        options.compilerArgs.add("-Xlint:deprecation")
    }

    tasks
        .withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>()
        .configureEach {
            compilerOptions {
                jvmTarget.set(jvmTargetVersion)
                // languageVersion.set(kotlinLanguageVersion)
                // apiVersion.set(kotlinApiVersion)

                // Enable support for experimental features
                // freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
                optIn.add("kotlin.RequiresOptIn")
                // freeCompilerArgs = listOf("-XXLanguage:+WhenGuards")
            }
            // compilerOptions.jvmTarget.set(jvmTargetVersion)
            // compilerOptions.apiVersion.set(kotlinVersion)
            // compilerOptions.languageVersion.set(kotlinVersion)
        }
}

// https://medium.com/kacper.wojciechowski/kotlin-2-0-android-project-migration-guide-b1234fbbff65
// fun Project.configureCompilerOptions() {
//     extensions.getByType<ComposeCompilerGradlePluginExtension>().apply {
//         enableStrongSkippingMode = true
//         includeSourceInformation = true
//     }
// }

/**
 * The application level default configurations.
 * You just need to add your custom properties as you wish.
 *
 * **Attention**:
 * The default value of `applicationId` is `namespace`.
 */
fun Project.configureApplication() {
    extensions.configure<ApplicationExtension>("android") {
        compileSdk = rootProject.libs.versions.compile.sdk.get().toInt()
        defaultConfig {
            minSdk = rootProject.libs.versions.min.sdk.get().toInt()
            targetSdk = rootProject.libs.versions.target.sdk.get().toInt()
            applicationId = namespace
            vectorDrawables.useSupportLibrary = true
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        sourceSets.configureEach {
            java.directories.addAll(listOf("src/$name/kotlin", "src/$name/java"))
        }
        testOptions {
            unitTests {
                isReturnDefaultValues = true
                isIncludeAndroidResources = true
            }
        }
        compileOptions {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }
        buildTypes {
            getByName("release") {
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                )
                isShrinkResources = true
                isMinifyEnabled = true
            }
            getByName("debug") {
                isShrinkResources = false
                isMinifyEnabled = false
            }
        }
        buildFeatures.viewBinding = true
        lint {
            disable += setOf("RtlHardcoded", "RtlCompat", "RtlEnabled")
        }
        packaging {
            resources.pickFirsts += setOf("META-INF/atomicfu.kotlin_module")
            resources.excludes +=
                setOf(
                    "META-INF/licenses/**",
                    "META-INF/NOTICE*",
                    "META-INF/LICENSE*",
                    "META-INF/DEPENDENCIES*",
                    "META-INF/INDEX.LIST",
                    "META-INF/io.netty.versions.properties",
                    "META-INF/services/reactor.blockhound.integration.BlockHoundIntegration",
                    "META-INF/{AL2.0,LGPL2.1}",
                    "**/*.proto",
                    "**/*.bin",
                    "**/*.java",
                )
        }
    }
}

/**
 * All the submodules will have the hierarchy configurations.
 * You just need to add your custom properties as you wish.
 */
fun Project.configureLibrary() {
    extensions.configure<LibraryExtension>("android") {
        compileSdk = rootProject.libs.versions.compile.sdk.get().toInt()
        defaultConfig {
            minSdk = rootProject.libs.versions.min.sdk.get().toInt()
            consumerProguardFiles("consumer-rules.pro")
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        sourceSets.configureEach {
            java.directories.addAll(listOf("src/$name/kotlin", "src/$name/java"))
        }
        testOptions {
            unitTests {
                isReturnDefaultValues = true
                isIncludeAndroidResources = true
            }
        }
        compileOptions {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }
        buildTypes {
            getByName("release") {
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                )
                isMinifyEnabled = false
            }
            getByName("debug") {
                isMinifyEnabled = false
            }
        }
        buildFeatures.viewBinding = true
        lint {
            disable += setOf("RtlHardcoded", "RtlCompat", "RtlEnabled")
        }
        packaging {
            resources.pickFirsts += setOf("META-INF/atomicfu.kotlin_module")
            resources.excludes +=
                setOf(
                    "META-INF/licenses/**",
                    "META-INF/NOTICE*",
                    "META-INF/LICENSE*",
                    "META-INF/DEPENDENCIES*",
                    "META-INF/INDEX.LIST",
                    "META-INF/io.netty.versions.properties",
                    "META-INF/services/reactor.blockhound.integration.BlockHoundIntegration",
                    "META-INF/{AL2.0,LGPL2.1}",
                    "**/*.proto",
                    "**/*.bin",
                    "**/*.java",
                )
        }
    }
}

// Target version of the generated JVM bytecode. It is used for type resolution.
tasks.withType<Detekt>().configureEach {
    jvmTarget = jvmTargetVersion.target

    reports {
        // observe findings in your browser with structure and code snippets
        html.required.set(true)
        // checkstyle like format mainly for integrations like Jenkins
        xml.required.set(true)
        // similar to the console output, contains issue signature to manually edit baseline files
        txt.required.set(true)
        // standardized SARIF format (https://sarifweb.azurewebsites.net/)
        // to support integrations with GitHub Code Scanning
        sarif.required.set(true)
        // simple Markdown format
        md.required.set(true)
    }
}

/*
 * Mimics all static checks that run on CI.
 * Note that this task is intended to run locally (not on CI), because on CI we prefer to have parallel execution
 * and separate reports for each of the checks (multiple statuses e.g. on GitHub PR page).
 */
tasks.register("staticCheck", fun Task.() {
    group = "verification"

    afterEvaluate {
        // Filter modules with "lintDebug" task (non-Android modules do not have lintDebug task)
        val lintTasks = subprojects.mapNotNull { "${it.name}:lintDebug" }

        // Get modules with "testDebugUnitTest" task (app module does not have it)
        val testTasks = subprojects.mapNotNull { "${it.name}:testDebugUnitTest" }
            .filter { it != "app:testDebugUnitTest" }

        // All task dependencies
        val taskDependencies =
            mutableListOf("app:assembleAndroidTest", "ktlintCheck", "detekt").also {
                it.addAll(lintTasks)
                it.addAll(testTasks)
            }

        // By defining Gradle dependency all dependent tasks will run before this "empty" task
        dependsOn(taskDependencies)
    }
})

// https://github.com/ben-manes/gradle-versions-plugin
tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any {
        version.uppercase(Locale.getDefault()).contains(it)
    }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

// --------------------------------------
/** Takes value from Gradle project property and sets it as build config property. */
// @Suppress("unused")
// fun BaseFlavor.buildConfigFieldFromGradleProperty(gradlePropertyName: String) {
//     val propertyValue = project.properties[gradlePropertyName] as? String
//     checkNotNull(propertyValue) { "Gradle property $gradlePropertyName is null" }
//
//     val androidResourceName = "GRADLE_${gradlePropertyName.toSnakeCase()}".uppercase()
//     buildConfigField("String", androidResourceName, propertyValue)
// }

/** Adds a new field to the generated BuildConfig class. */
// @Suppress("unused")
// fun DefaultConfig.buildConfigField(name: String, value: Array<String>) {
//     // Create String that holds Java String Array code
//     val strValue = value.joinToString(prefix = "{", separator = ",", postfix = "}", transform = { "\"$it\"" })
//     buildConfigField("String[]", name, strValue)
// }

fun String.toSnakeCase() = this.split(Regex("(?=[A-Z])")).joinToString("_") { it.uppercase() }
