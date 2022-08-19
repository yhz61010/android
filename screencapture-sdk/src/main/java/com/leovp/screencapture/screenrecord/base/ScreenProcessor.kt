package com.leovp.screencapture.screenrecord.base

import android.graphics.Bitmap
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Size

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午2:10
 */
interface ScreenProcessor {

    companion object {
        @Suppress("unused")
        const val NAL_SLICE = 1

        //        const val NAL_SLICE_DPA = 2
        //        const val NAL_SLICE_DPB = 3
        //        const val NAL_SLICE_DPC = 4
        @Suppress("unused")
        const val NAL_SLICE_IDR = 5

        //        const val NAL_SEI = 6
        @Suppress("unused")
        const val NAL_SPS = 7

        @Suppress("unused")
        const val NAL_PPS = 8
        //        const val NAL_AUD = 9
        //        const val NAL_FILLER = 12
    }

    /**
     * This method must be called on main thread.
     */
    fun onInit()
    fun onStart()

    /**
     * After `onStop()`, you can do `onStart()` again.
     * However, once do `onRelease()` you can not start again. Please call `onInit()` then `onStart()`
     */
    fun onStop()

    /**
     * Once do `onRelease()` you can not start again. Please call `onInit()` then `onStart()`
     */
    fun onRelease()

    // ==================================================

    /**
     * This method must be called on main thread.
     */
    fun changeOrientation() {}

    fun takeScreenshot(width: Int? = null, height: Int? = null, result: (bitmap: Bitmap) -> Unit) {}

    fun getVideoSize(): Size

    // ==================================================

    fun computePresentationTimeUs(frameIndex: Long, fps: Float): Long = (frameIndex * 1_000_000 / fps).toLong()

    fun getCodecListByMimeType(mimeType: String, encoder: Boolean = true): List<MediaCodecInfo> = MediaCodecList(MediaCodecList.REGULAR_CODECS) // MediaCodecList.ALL_CODECS
        .codecInfos.filter { it.isEncoder == encoder }.filter { it.supportedTypes.indexOfFirst { type -> type.equals(mimeType, true) } > -1 }

    fun getHevcCodec(encoder: Boolean = true): List<MediaCodecInfo> = getCodecListByMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC, encoder)
}
