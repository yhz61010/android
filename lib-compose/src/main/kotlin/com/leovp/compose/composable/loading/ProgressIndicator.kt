package com.leovp.compose.composable.loading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Author: Michael Leo
 * Date: 2025/7/29 14:22
 */

@Suppress("FunctionNaming")
@Composable
fun ProgressIndicator(
    modifier: Modifier = Modifier,
    bgColor: Color = Color(0x22000000),
    color: Color = MaterialTheme.colorScheme.secondary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.width(64.dp),
            color = color,
            trackColor = trackColor
        )
    }
}

@Suppress("FunctionNaming")
@Preview(name = "ProgressIndicator")
@Composable
fun PreviewProgressIndicator() {
    ProgressIndicator()
}
