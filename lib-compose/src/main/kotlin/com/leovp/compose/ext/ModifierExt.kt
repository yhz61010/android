@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.compose.ext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Author: Michael Leo
 * Date: 2023/7/27 16:12
 */

fun Modifier.noRippleClickable(onClick: () -> Unit) = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

/**
 * Usage:
 * ```
 * Row(modifier = Modifier.bottomBorder(DarkGray, 1.d)) { }
 * ```
 *
 * https://stackoverflow.com/a/68595142
 */
fun Modifier.bottomBorder(color: Color, strokeWidth: Dp = 1.dp) = composed(
    factory = {
        val density = LocalDensity.current
        val strokeWidthPx = density.run { strokeWidth.toPx() }

        Modifier.drawBehind {
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
)
