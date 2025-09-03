@file:Suppress("unused")

package com.leovp.compose.ext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 2025/9/2 15:43
 */

/**
 * Usage:
 * ```
 * val debounceHandler = rememberDebounceClickHandler(onClick = {})
 * Button(onClick = debounceHandler) {
 *     Text(text = "OK")
 * }
 * ```
 */
@Composable
fun rememberDebounceClickHandler(
    debounceTime: Long = 1000L,
    onClick: () -> Unit,
): () -> Unit {
    var isClickable by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    return remember {
        {
            if (isClickable) {
                isClickable = false
                onClick()
                scope.launch {
                    delay(debounceTime)
                    isClickable = true
                }
            }
        }
    }
}

/**
 * All buttons share a single timestamp (_lastClickTime_), affecting each other.
 *
 * Usage:
 * ```
 * val generalDebounceHandler = rememberDebounceHandler()
 * Button(
 *     onClick = {
 *         generalDebounceHandler.handle(1000L) {  }
 *     }
 * ) { Text(text = "OK") }
 * ```
 */
@Composable
fun rememberDebounceHandler(debounceTime: Long = 1000L): DebounceHandler {
    return remember { DebounceHandler(debounceTime) }
}

/**
 * Advanced version of _rememberDebounceHandler_.
 * Allow to set different _debounceTime_ for different buttons.
 *
 * Usage:
 * ```
 * val multiDebounceHandler = rememberMultiDebounceHandler()
 * Button(
 *     onClick = {
 *         multiDebounceHandler.handle("btn_like", 300L) { }
 *     }
 * ) { Text(text = "Like") }
 * Button(
 *     onClick = {
 *         multiDebounceHandler.handle("btn_subscribe", 1000L) { }
 *     }
 * ) { Text(text = "Subscribe") }
 * ```
 */
@Composable
fun rememberMultiDebounceHandler(): MultiDebounceHandler {
    return remember { MultiDebounceHandler() }
}

/**
 * Usage:
 * ```
 * DebounceButton(onClick = {  }) { Text(text = "OK") }
 * ```
 */
@Suppress("LongParameterList", "FunctionNaming")
@Composable
fun DebounceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    debounceTime: Long = 1000L,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    var isClickable by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            if (isClickable && enabled) {
                isClickable = false
                onClick()
                scope.launch {
                    delay(debounceTime)
                    isClickable = true
                }
            }
        },
        modifier = modifier,
        enabled = enabled && isClickable,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * All buttons share a single timestamp (_lastClickTime_), affecting each other.
 */
class DebounceHandler(private val defaultDebounceTime: Long = 1000L) {
    private var lastClickTime = 0L

    fun handle(debounceTime: Long = defaultDebounceTime, action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > debounceTime) {
            lastClickTime = currentTime
            action()
        }
    }
}

/**
 * Advanced version of DebounceHandler.
 * Allow to set different _debounceTime_ for different buttons.
 */
class MultiDebounceHandler {
    private val debounceMap = mutableMapOf<String, Long>()

    fun handle(
        key: String,
        debounceTime: Long = 1000L,
        action: () -> Unit,
    ) {
        val currentTime = System.currentTimeMillis()
        val lastTime = debounceMap[key] ?: 0L

        if (currentTime - lastTime > debounceTime) {
            debounceMap[key] = currentTime
            action()
        }
    }
}
