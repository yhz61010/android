package com.leovp.screencapture.screenrecord.base

import android.graphics.Bitmap
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Size

/**
 * Author: Michael Leo
 * Date: 20-5-15 下午2:10
 *
 * Implementations that need presentation timestamps must derive them from the input source's
 * monotonic timeline; the removed frame-index helper could not represent dropped or delayed input.
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
     * Allocates the resources this processor needs. Call it on the main thread unless the
     * implementation documents otherwise.
     *
     * Implementations may tighten this contract; check the concrete strategy. A one-shot
     * strategy rejects a second initialization and rejects initializing a released instance.
     */
    fun onInit()
    fun onStart()

    /**
     * Stops producing data.
     *
     * By default `onStart()` may follow, and only `onRelease()` is final. **A one-shot
     * implementation may treat `onStop()` as equivalent to `onRelease()`**; check the concrete
     * strategy before relying on a restart.
     *
     * Implementations may throw; check the concrete strategy's contract. A strategy that
     * aggregates teardown failures may attempt every step before reporting the first failure with
     * the remaining ones attached as suppressed. Callers must not let such a failure escape from
     * a `Handler` runnable or another context where an uncaught throwable takes the process down.
     */
    fun onStop()

    /**
     * Releases every owned resource. A released processor is never restarted. Whether a fresh
     * `onInit()` can revive this instance is up to the implementation; a one-shot strategy
     * requires a new instance instead.
     *
     * Implementations may throw; check the concrete strategy's contract. A strategy that
     * aggregates teardown failures may attempt every step before reporting the first failure with
     * the remaining ones attached as suppressed.
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

    // MediaCodecList.ALL_CODECS
    fun getCodecListByMimeType(mimeType: String, encoder: Boolean = true): List<MediaCodecInfo> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .filter {
                it.isEncoder == encoder
            }
            .filter {
                it.supportedTypes.indexOfFirst { type -> type.equals(mimeType, true) } > -1
            }

    fun getHevcCodec(encoder: Boolean = true): List<MediaCodecInfo> =
        getCodecListByMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC, encoder)
}
