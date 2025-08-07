package com.leovp.audio.mediacodec

import android.media.AudioFormat
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
abstract class BaseMediaCodec(
    private val codecName: String,
    protected open val sampleRate: Int,
    protected open val channelCount: Int,
    protected open val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val isEncoding: Boolean = false,
) : IAudioMediaCodec {
    companion object {
        private const val TAG = "BaseMediaCodec"
    }

    internal lateinit var format: MediaFormat
    internal lateinit var codec: MediaCodec

    protected val ioScope = CoroutineScope(Dispatchers.IO + CoroutineName("base-mediacodec"))

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
        flush()

        // These are the magic lines for Samsung phone. DO NOT try to remove or refactor me.
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
        LogContext.log.w(TAG, "createMediaFormat() codec=$codecName sampleRate=$sampleRate channelCount=$channelCount")
        format = MediaFormat.createAudioFormat(codecName, sampleRate, channelCount)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8 * 1024)
        setFormatOptions(format)
        LogContext.log.w(TAG, "format=$format")
    }

    /**
     * Most of time, you do NOT need to override this method.
     */
    open fun createCodec() {
        LogContext.log.w(TAG, "createCodec() codec=$codecName isEncoding=$isEncoding")
        codec = if (isEncoding) MediaCodec.createEncoderByType(codecName) else MediaCodec.createDecoderByType(codecName)
        codec.configure(format, null, null, if (isEncoding) MediaCodec.CONFIGURE_FLAG_ENCODE else 0)
        setMediaCodecOptions(codec)
    }

    protected fun getBytesPerSample(): Int = when (audioFormat) {
        AudioFormat.ENCODING_PCM_8BIT -> 1

        AudioFormat.ENCODING_PCM_16BIT,
        AudioFormat.ENCODING_IEC61937,
        AudioFormat.ENCODING_DEFAULT -> 2

        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3

        AudioFormat.ENCODING_PCM_FLOAT,
        AudioFormat.ENCODING_PCM_32BIT -> 4

        else -> throw IllegalArgumentException("Bad audio format $audioFormat")
    }
}
