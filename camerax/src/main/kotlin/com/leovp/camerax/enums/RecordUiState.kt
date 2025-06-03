package com.leovp.camerax.enums

/**
 * Camera UI  states and inputs
 *
 * Author: Michael Leo
 */
enum class RecordUiState {
    IDLE, // Not recording, all UI controls are active.
    RECORDING, // Camera is recording, only display Pause/Resume & Stop button.
    FINALIZED // Recording just completes, disable all RECORDING UI controls.
    //        RECOVERY   // For future use.
}
