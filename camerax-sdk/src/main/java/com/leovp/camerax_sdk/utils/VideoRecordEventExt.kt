package com.leovp.camerax_sdk.utils

import androidx.camera.video.VideoRecordEvent

/**
 * A helper extended function to get the name(string) for the VideoRecordEvent.
 */
fun VideoRecordEvent.getNameString(): String {
    return when (this) {
        is VideoRecordEvent.Status   -> "Status"
        is VideoRecordEvent.Start    -> "Started"
        is VideoRecordEvent.Finalize -> "Finalized"
        is VideoRecordEvent.Pause    -> "Paused"
        is VideoRecordEvent.Resume   -> "Resumed"
        else                         -> throw IllegalArgumentException("Unknown VideoRecordEvent: $this")
    }
}
