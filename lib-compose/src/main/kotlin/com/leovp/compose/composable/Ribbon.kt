package com.leovp.compose.composable

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leovp.compose.utils.previewInitLog

/**
 * Author: Michael Leo
 * Date: 2025/9/4 14:10
 */
@Suppress("FunctionNaming")
@Composable
fun BoxScope.Ribbon(
    text: String,
    modifier: Modifier = Modifier,
    width: Dp = 240.dp,
    ribbonHeight: Dp = 25.dp,
    textColor: Color = Color(0xFFFF9900),
    bgColor: Color = Color(0xFFFFF7E6),
    fontSize: TextUnit = 16.sp,
) {
    val translationX = width.value / 4f
    Box(
        modifier =
        modifier
            .align(Alignment.TopEnd)
            .requiredSize(width = width * 2f, height = ribbonHeight)
            // .width(dp(width))
            .graphicsLayer {
                rotationZ = 45f
                this.translationX = translationX
            }
            .background(bgColor)
    ) {
        // Box(
        //     modifier = Modifier
        //         .matchParentSize()
        //         // .background(bgColor)
        //         .requiredHeight(dp(ribbonHeight))
        // )

        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            modifier =
            Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    this.translationX = translationX / 1.8f
                }
        )
    }
}

@Suppress("FunctionNaming")
@Preview(uiMode = UI_MODE_NIGHT_NO)
@Composable
fun PreviewRibbon() {
    previewInitLog()
    Box(modifier = Modifier.size(180.dp, 180.dp)) {
        Ribbon(
            text = "Aged check"
        )
    }
}
