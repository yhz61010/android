package com.leovp.camera2live.base.iters

import android.media.Image

/**
 * Author: Michael Leo
 * Date: 20-4-1 上午11:05
 */
interface IDataProcessStrategy {
    /**
     * Processes one camera frame.
     *
     * [cameraSensorOrientation] is retained for source and binary compatibility and may be `-1`.
     * Built-in implementations use constructor-provided recording rotation instead.
     */
    fun doProcess(image: Image, lensFacing: Int, cameraSensorOrientation: Int): ByteArray
}
