package com.leovp.compose.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.leovp.compose.utils.previewInitLog

/**
 * Author: Michael Leo
 * Date: 2025/9/23 11:02
 */

@Suppress("FunctionNaming")
@Composable
fun UnderlineTextField(value: String, onValueChange: (String) -> Unit, placeholder: String,) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
        Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused },
        textStyle = MaterialTheme.typography.headlineMedium,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Column {
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    innerTextField()
                    if (value.isEmpty()) {
                        Text(
                            modifier = Modifier.align(Alignment.CenterStart),
                            text = placeholder,
                            color = Color(0xFFAAAAAA),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
                // Underline
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            color =
                            when {
                                isFocused -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline
                            },
                            shape = RectangleShape
                        )
                )
            }
        }
    )
}

@Suppress("FunctionNaming")
@Preview
@Composable
fun PreviewLoginScreen() {
    previewInitLog()
    Row(modifier = Modifier.height(200.dp)) {
        UnderlineTextField(
            value = "",
            onValueChange = { },
            placeholder = "Please input"
        )
    }
}
