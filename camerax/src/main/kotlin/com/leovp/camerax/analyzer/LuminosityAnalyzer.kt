@file:Suppress("unused")

package com.leovp.camerax.analyzer

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.ArrayList

/** Helper type alias used for analysis use case callbacks */
typealias LumaListener = (luma: Double) -> Unit

/**
 * Sample every Nth luma byte when computing the average. The luminosity metric is approximate, so
 * striding keeps it statistically representative while cutting per-frame work proportionally.
 */
private const val LUMA_SAMPLE_STRIDE = 10

/**
 * Our custom image analysis class.
 *
 * <p>All we need to do is override the function `analyze` with our desired operations. Here,
 * we compute the average luminosity of the image by looking at the Y plane of the YUV frame.
 */
internal class LuminosityAnalyzer(listener: LumaListener? = null) : ImageAnalysis.Analyzer {
    private val frameRateWindow = 8

    // Ring buffer of the most recent frame timestamps. A primitive LongArray avoids the per-frame
    // Long autoboxing that an ArrayDeque<Long> incurred on this hot path.
    private val frameTimestamps = LongArray(frameRateWindow)
    private var frameTimestampCount = 0
    private var frameTimestampHead = -1

    private val listeners = ArrayList<LumaListener>().apply { listener?.let { add(it) } }
    private var lastAnalyzedTimestamp = 0L
    var framesPerSecond: Double = -1.0
        private set

    /**
     * Used to add listeners that will be called with each luma computed
     */
    fun onFrameAnalyzed(listener: LumaListener) = listeners.add(listener)

    /**
     * Analyzes an image to produce a result.
     *
     * <p>The caller is responsible for ensuring this analysis method can be executed quickly
     * enough to prevent stalls in the image acquisition pipeline. Otherwise, newly available
     * images will not be acquired and analyzed.
     *
     * <p>The image passed to this method becomes invalid after this method returns. The caller
     * should not store external references to this image, as these references will become
     * invalid.
     *
     * @param image image being analyzed VERY IMPORTANT: Analyzer method implementation must
     * call image.close() on received images when finished using them. Otherwise, new images
     * may not be received or the camera may stall, depending on back pressure setting.
     *
     */
    override fun analyze(image: ImageProxy) {
        image.use { frame ->
            // If there are no listeners attached, we don't need to perform analysis
            if (listeners.isEmpty()) return

            // Keep track of frames analyzed using a moving average of frame timestamps.
            val currentTime = System.currentTimeMillis()
            frameTimestampHead = (frameTimestampHead + 1) % frameRateWindow
            frameTimestamps[frameTimestampHead] = currentTime
            if (frameTimestampCount < frameRateWindow) frameTimestampCount++

            val oldestIndex =
                (frameTimestampHead - frameTimestampCount + 1 + frameRateWindow) % frameRateWindow
            val timestampLast = frameTimestamps[oldestIndex]
            framesPerSecond = 1.0 /
                (
                    (currentTime - timestampLast) /
                        frameTimestampCount.coerceAtLeast(1).toDouble()
                    ) * 1000.0

            // Analysis could take an arbitrarily long amount of time
            // Since we are running in a different thread, it won't stall other use cases
            lastAnalyzedTimestamp = currentTime
            // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance plane.
            // Read the plane in place (no per-frame copy) and sub-sample it.
            val luma = averageLuma(frame.planes[0].buffer, LUMA_SAMPLE_STRIDE)
            // Call all listeners with new value
            listeners.forEach { it(luma) }
        }
    }
}

/**
 * Average of the unsigned luma bytes in [buffer], sampling every [stride]-th byte starting from the
 * buffer's current position. Reads the buffer in place without copying; a [stride] of 1 sums every
 * byte.
 */
internal fun averageLuma(buffer: ByteBuffer, stride: Int = 1): Double {
    val end = buffer.limit()
    var i = buffer.position()
    if (i >= end) return 0.0
    var sum = 0L
    var count = 0
    while (i < end) {
        sum += buffer.get(i).toInt() and 0xFF
        count++
        i += stride
    }
    return if (count == 0) 0.0 else sum.toDouble() / count
}
