package com.leovp.floatview

/**
 * Result of attempting to create and attach a float view.
 *
 * [FAILED_BUT_ATTACHED_HIDDEN] keeps the manager registration so the caller can retry removal.
 */
enum class FloatViewShowResult {
    /** The custom view is visible and managed by WindowManager. */
    SHOWN,

    /** The attempt failed and the custom view is not managed by WindowManager. */
    FAILED_AND_DETACHED,

    /** The attempt failed; removal also failed, so the hidden view remains managed. */
    FAILED_BUT_ATTACHED_HIDDEN,
}
