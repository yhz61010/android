@file:Suppress("unused")

package com.leovp.compose.composable.event

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.leovp.mvvm.BaseViewModel
import com.leovp.mvvm.ScreenCountdownManager.CountdownEvent

/**
 * Author: Michael Leo
 * Date: 2025/11/18 16:40
 */

@Composable
fun DispatchTouchEvent(
    interactionSource: MutableInteractionSource,
    viewModel: BaseViewModel<*, *>,
) {
    LaunchedEffect(interactionSource) {
        // interactionSource.interactions.collect {
        //     viewModel
        //         .requiredScreenCountdownComponent
        //         .handleCountdownEvent(CountdownEvent.OnUserInteraction)
        // }

        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press ->
                    viewModel
                        .requiredScreenCountdownComponent
                        .handleCountdownEvent(CountdownEvent.OnUserInteraction)

                is PressInteraction.Release -> Unit
                is PressInteraction.Cancel -> Unit
            }
        }
    }
}
