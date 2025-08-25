@file:Suppress("unused")

package com.leovp.compose.composable.event

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Author: Michael Leo
 * Date: 2025/8/21 10:33
 */

class UiEventManager {
    private val eventChannel = Channel<UiEvent>(Channel.UNLIMITED)
    val events = eventChannel.receiveAsFlow()

    suspend fun sendEvent(event: UiEvent) {
        eventChannel.send(event)
    }

    fun sendEventSync(event: UiEvent) {
        eventChannel.trySend(event)
    }
}
