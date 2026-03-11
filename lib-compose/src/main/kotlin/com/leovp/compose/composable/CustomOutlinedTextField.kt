package com.leovp.compose.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Author: Michael Leo
 * Date: 2026/3/6 17:15
 */

@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun CustomOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    border: BorderStroke = BorderStroke(width = 1.dp, color = Color.Gray),
    shape: Shape = MaterialTheme.shapes.medium,
    contentPaddingValues: PaddingValues = PaddingValues(),
    singleLine: Boolean = false,
    placeholderText: String? = null,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = TextStyle.Default,
    isError: Boolean = false,
    errorBorderStroke: BorderStroke = BorderStroke(width = 1.dp, color = Color.Red),
    errorBgColor: Color = Color.LightGray,
) {
    var borderStroke = border
    var errorBgModifier: Modifier = Modifier

    if (isError) {
        borderStroke = errorBorderStroke
        errorBgModifier = Modifier.background(errorBgColor)
    }
    Column {
        // ========== Main input area ==========
        Box(
            modifier = modifier
                .border(border.width, borderStroke.brush, shape)
                .then(errorBgModifier)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            // ========== Basic input components ==========
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPaddingValues),
                keyboardOptions = keyboardOptions,
                textStyle = textStyle,

                // ========== Placeholder ==========
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty() && !placeholderText.isNullOrEmpty()) {
                            Text(
                                text = placeholderText,
                                color = Color.Gray
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        supportingText?.let { supportingTextComposable ->
            Spacer(Modifier.height(4.dp))
            supportingTextComposable.invoke()
        }
    }
}

@Suppress("FunctionNaming")
@Preview
@Composable
fun PreviewCustomOutlinedTextField() {
    CustomOutlinedTextField(
        value = "",
        onValueChange = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        singleLine = true,
        placeholderText = "0",
        supportingText = {
            Text(
                text = "Error Found",
                color = Color.Red,
                style = MaterialTheme.typography.bodySmall
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        isError = false
    )
}
