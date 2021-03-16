package com.leovp.screenshot.base

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午1:51
 */
interface ScreenDataListener {
    /**
     * Only used by [ScreenCapture.BY_IMAGE] and [ScreenCapture.BY_MEDIA_CODEC]
     *
     * @param buffer For [ScreenCapture.BY_IMAGE] its type is `Image`.
     * **Attention**: There is no need to call [Image.close()] method when you finish your processing.
     * Actually, DO **NOT** close the `Image` by yourself. It will be closed safely by the framework.
     *
     * @param flags Only works for [ScreenCapture.BY_IMAGE] and [ScreenCapture.BY_MEDIA_CODEC]. In other cases, `-1` will be returned.
     */
    fun onDataUpdate(buffer: Any, flags: Int = -1)
}