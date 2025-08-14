@file:Suppress("unused")

package com.leovp.compose.viewmodel.lifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Author: Michael Leo
 * Date: 2025/4/7 10:55
 */

/**
 * Usage:
 * ```kotlin
 * @Composable
 * fun MyScreen(viewModel: MyViewModel = viewModel()) {
 *     OnResume {
 *         viewModel.onResumeLogic()
 *     }
 *
 *     // UI content here...
 * }
 * ```
 */
@Suppress("FunctionNaming")
@Composable
fun OnLifecycleEvent(lifecycleEvent: Lifecycle.Event, onEvent: () -> Unit) {
    val currentOnEvent by rememberUpdatedState(onEvent)

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == lifecycleEvent) {
                currentOnEvent()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun OnResume(onEvent: () -> Unit) = OnLifecycleEvent(Lifecycle.Event.ON_RESUME, onEvent)

@Suppress("FunctionNaming")
@Composable
fun OnPause(onEvent: () -> Unit) = OnLifecycleEvent(Lifecycle.Event.ON_PAUSE, onEvent)

@Suppress("FunctionNaming")
@Composable
fun OnStart(onEvent: () -> Unit) = OnLifecycleEvent(Lifecycle.Event.ON_START, onEvent)

@Suppress("FunctionNaming")
@Composable
fun OnStop(onEvent: () -> Unit) = OnLifecycleEvent(Lifecycle.Event.ON_STOP, onEvent)

@Suppress("FunctionNaming")
@Composable
fun OnCreate(onEvent: () -> Unit) = OnLifecycleEvent(Lifecycle.Event.ON_CREATE, onEvent)

@Suppress("FunctionNaming")
@Composable
fun OnDestroy(onEvent: () -> Unit) = OnLifecycleEvent(Lifecycle.Event.ON_DESTROY, onEvent)
