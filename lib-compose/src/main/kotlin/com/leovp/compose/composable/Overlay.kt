package com.leovp.compose.composable

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
fun BoxScope.ImageOverlay(
    text: String,
    modifier: Modifier = Modifier,
    maskColor: Color = Color(0x4D000000),
    textColor: Color = Color.White,
    textBgColor: Color = Color(0xFF666666),
    fontSize: TextUnit = 20.sp,
    contentAlignment: Alignment = Alignment.Center,
) {
    Box(
        modifier =
        Modifier
            .matchParentSize()
            .background(maskColor),
        contentAlignment = contentAlignment
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            modifier =
            modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    rotationZ = -10f
                }
                .clip(MaterialTheme.shapes.small)
                .background(textBgColor)
                .padding(18.dp, 2.dp)
        )
    }
}

@Suppress("FunctionNaming")
@Preview(uiMode = UI_MODE_NIGHT_NO)
@Composable
fun PreviewImageOverlay() {
    previewInitLog()
    Box(modifier = Modifier.size(180.dp, 120.dp)) {
        ImageOverlay(text = "Sold out", modifier = Modifier.fillMaxWidth(0.8f))
    }
}
