package com.leovp.screencapture.screenrecord.base

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午1:51
 */
interface ScreenDataListener {
    /**
     * @param flags Only works for [ScreenCapture.BY_IMAGE_2_H26x] and
     * [ScreenCapture.BY_MEDIA_CODEC]. In other cases, `-1` will be returned.
     * @param presentationTimeUs Only works for [ScreenCapture.BY_IMAGE_2_H26x] and
     * [ScreenCapture.BY_MEDIA_CODEC]. In other cases, `-1` will be returned.
     */
    fun onDataUpdate(buffer: Any, flags: Int = -1, presentationTimeUs: Long = -1)

    /**
     * Called after a screen processor fails asynchronously and releases its owned resources.
     * Intentional cancellation or an explicit release does not invoke this callback.
     */
    fun onError(error: Throwable) {}
}
