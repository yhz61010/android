package com.leovp.screenshot.base

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午1:51
 */
interface ScreenDataListener {
    /**
     * @param flags Only works for [ScreenCapture.BY_IMAGE_2_H264] and [ScreenCapture.BY_MEDIA_CODEC]. In other cases, `-1` will be returned.
     */
    fun onDataUpdate(buffer: Any, flags: Int = -1)
}