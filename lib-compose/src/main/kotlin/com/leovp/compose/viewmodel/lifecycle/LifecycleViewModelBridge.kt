@file:Suppress("unused")

package com.leovp.compose.viewmodel.lifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Author: Michael Leo
 * Date: 2025/4/7 10:24
 */

/**
 * Usage:
 * ```kotlin
 * @Composable
 * fun MyScreen(viewModel: MyViewModel = viewModel()) {
 *     LifecycleViewModelBridge(viewModel)
 *     // UI content here...
 * }
 * ```
 */
@Composable
fun <VM> LifecycleViewModelBridge(
    viewModel: VM,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) where VM : ViewModel, VM : LifecycleAware {
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = viewModel.onResume()
            override fun onPause(owner: LifecycleOwner) = viewModel.onPause()
            override fun onStart(owner: LifecycleOwner) = viewModel.onStart()
            override fun onStop(owner: LifecycleOwner) = viewModel.onStop()
            override fun onDestroy(owner: LifecycleOwner) = viewModel.onDestroy()
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
