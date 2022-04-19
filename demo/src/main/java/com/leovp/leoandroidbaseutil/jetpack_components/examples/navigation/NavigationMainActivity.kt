package com.leovp.leoandroidbaseutil.jetpack_components.examples.navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityNavigationMainBinding

/**
 * ```xml
 * implementation "androidx.navigation:navigation-fragment-ktx:$navVersion"
 * implementation "androidx.navigation:navigation-ui-ktx:$navVersion"
 * ```
 *
 * [Ensure type-safety by using Safe Args](https://developer.android.com/guide/navigation/navigation-getting-started#ensure_type-safety_by_using_safe_args)
 *
 * Add the following `classpath` in **TOP** level `build.gradle` file.
 * ```kotlin
 * def nav_version = "2.4.2"
 * classpath "androidx.navigation:navigation-safe-args-gradle-plugin:$nav_version"
 * ```
 * Then, add the following codes in your **Project** level `build.gradle` file to enable plugin:
 * ```
 * plugins {
 *     # Use only one of the following plugin:
 *     # apply plugin: 'androidx.navigation.safeargs'
 *     id 'androidx.navigation.safeargs'
 *     # Alternatively, to generate Kotlin code suitable for Kotlin-only modules add:
 *     # apply plugin: 'androidx.navigation.safeargs.kotlin'
 *     id 'androidx.navigation.safeargs.kotlin'
 * }
 * ```
 */
class NavigationMainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNavigationMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigationMainBinding.inflate(layoutInflater).apply { setContentView(root) }

//        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
//        val navController = navHostFragment.navController
        // setupBottomNavMenu(navController)

//        val bundle = Bundle()
//        bundle.putString("param1", "Parameter One")
//        bundle.putString("param2", "Parameter Two")
//        navController.navigate(R.id.fragmentForNavFirst, bundle)
    }
}