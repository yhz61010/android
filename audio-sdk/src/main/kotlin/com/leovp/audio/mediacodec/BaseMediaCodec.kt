package com.leovp.audio.mediacodec

import android.media.MediaCodec
import android.media.MediaFormat
import com.leovp.audio.mediacodec.iter.IAudioMediaCodec
import com.leovp.log.LogContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

/**
 * Author: Michael Leo
 * Date: 2023/5/4 10:18
 */
abstract class BaseMediaCodec(private val codecName: String,
    protected open val sampleRate: Int,
    protected open val channelCount: Int,
    private val isEncoding: Boolean = false) : IAudioMediaCodec {
    companion object {
        private const val TAG = "BaseMediaCodec"
    }

    internal lateinit var format: MediaFormat
    internal lateinit var codec: MediaCodec

    protected val ioScope = CoroutineScope(Dispatchers.IO + CoroutineName("base-mediacodec"))
    private var frameCount: Long = 0

    abstract fun setFormatOptions(format: MediaFormat)

    open fun setMediaCodecOptions(codec: MediaCodec) = Unit

    open fun start() {
        createMediaFormat()
        createCodec()
        codec.start()
    }

    open fun stop() {
        require(::codec.isInitialized) { "Did you call start() before?" }
        runCatching { codec.stop() }.onFailure { it.printStackTrace() }
    }

    /**
     * Release resource.
     */
    open fun release() {
        require(::codec.isInitialized) { "Did you call start() before?" }
        flush() // These are the magic lines for Samsung phone. DO NOT try to remove or refactor me.
        // runCatching { codec.setCallback(null) }.onFailure { it.printStackTrace() }
        runCatching { codec.release() }.onFailure { it.printStackTrace() }
        ioScope.cancel()
    }

    open fun flush() {
        require(::codec.isInitialized) { "Did you call start() before?" }
        runCatching { codec.flush() }.onFailure { it.printStackTrace() }
    }

    /**
     * Most of time, you do NOT need to override this method.
     */
    open fun createMediaFormat() {
        LogContext.log.i(TAG, "createMediaFormat() codec=$codecName sampleRate=$sampleRate channelCount=$channelCount")
        format = MediaFormat.createAudioFormat(codecName, sampleRate, channelCount)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8 * 1024)
        setFormatOptions(format)
    }

    /**
     * Most of time, you do NOT need to override this method.
     */
    open fun createCodec() {
        LogContext.log.i(TAG, "createCodec() codec=$codecName isEncoding=$isEncoding")
        codec = if (isEncoding) MediaCodec.createEncoderByType(codecName) else MediaCodec.createDecoderByType(codecName)
        codec.configure(format, null, null, if (isEncoding) MediaCodec.CONFIGURE_FLAG_ENCODE else 0)
        setMediaCodecOptions(codec)
    }

    /**
     * Calculate PTS.
     * Actually, it doesn't make any error if you return 0 directly.
     *
     * @return The calculated presentation time in microseconds.
     */
    private fun computePresentationTimeUs(frameIndex: Long,
        sampleRate: Int): Long { // LogContext.log.d(TAG, "computePresentationTimeUs=${frameIndex * 1_000_000L / sampleRate}")
        return frameIndex * 1_000_000L / sampleRate
    }

    override fun getPresentationTimeUs(): Long {
        return computePresentationTimeUs(++frameCount, sampleRate)
    }
}
