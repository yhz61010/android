@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.compose.ext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Author: Michael Leo
 * Date: 2023/7/27 16:12
 */

@Composable
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )

/**
 * Usage:
 * ```
 * Row(modifier = Modifier.bottomBorder(DarkGray, 1.d)) { }
 * ```
 *
 * https://stackoverflow.com/a/68595142
 */
@Composable
fun Modifier.bottomBorder(color: Color, strokeWidth: Dp = 1.dp): Modifier {
    val density = LocalDensity.current
    val strokeWidthPx = density.run { strokeWidth.toPx() }

    return this.drawBehind {
        val width = size.width
        val height = size.height - strokeWidthPx / 2

        drawLine(
            color = color,
            start = Offset(x = 0f, y = height),
            end = Offset(x = width, y = height),
            strokeWidth = strokeWidthPx
        )
    }
}

/**
 * Usage:
 * ```
 * Box(
 *     modifier = Modifier.debounceClickable(debounceTime = 1000L) { },
 * ) { }
 * ```
 */
@Composable
fun Modifier.debounceClickable(debounceTime: Long = 1000L, onClick: () -> Unit): Modifier {
    var lastClickTime by remember { mutableLongStateOf(0L) }

    return this.clickable {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > debounceTime) {
            lastClickTime = currentTime
            onClick()
        }
    }
}

fun Modifier.dispatchTouchEvent(
    // interactionSource: MutableInteractionSource? = null,
    onTouch: () -> Unit = {},
): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { change ->
                    when {
                        // Press
                        change.pressed && change.previousPressed.not() -> {
                            onTouch()
                        }
                        // Release
                        change.pressed.not() && change.previousPressed -> Unit
                        // Move
                        change.pressed && change.previousPressed -> Unit
                    }
                }
            }
        }
    }

// val source = interactionSource ?: remember { MutableInteractionSource() }

// clickable(
//     interactionSource = source,
//     indication = null,
//     onClick = onClick
// )

// pointerInput(Unit) {
//     detectTapGestures(
//         onPress = { _ ->
//             // When pressed, it will be executed.
//             // You can get position from `offset` parameter.
//
//             // Wait for release or cancel
//             val released = tryAwaitRelease()
//             if (released) {
//                 onClick()
//             } else {
//                 // Gesture cancelled.
//             }
//         }
//     )
// }
