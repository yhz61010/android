package com.leovp.camera2live.codec

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.bytes.toHexString
import com.leovp.camera2live.listeners.CallbackListener
import com.leovp.log.LogContext
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Author: Michael Leo
 * Date: 20-3-24 下午5:35
 */
class CameraAvcEncoder @JvmOverloads constructor(
    private val width: Int,
    private val height: Int,
    private val bitrate: Int,
    private val frameRate: Int,
    private val iFrameInterval: Int = DEFAULT_KEY_I_FRAME_INTERVAL,
    private val bitrateMode: Int = DEFAULT_BITRATE_MODE,
) {
    private val droppedFrameCount = AtomicLong(0)
    val queue: ConcurrentLinkedQueue<ByteArray> =
        BoundedFrameQueue(MAX_PENDING_FRAMES, ::onEncoderFrameDropped)
    private var dataUpdateCallback: CallbackListener? = null
    lateinit var h264Encoder: MediaCodec
        private set
    private var outputFormat: MediaFormat? = null
    private var codecCallbackThread: HandlerThread? = null
    private var codecCallbackHandler: Handler? = null
    private var dataCallbackThread: HandlerThread? = null
    private var dataCallbackHandler: Handler? = null
    private val inputBufferLock = Any()
    private val availableInputBufferIds = ArrayDeque<Int>()

    init {
        initEncoder()
    }

    @SuppressWarnings("unused")
    var csd: ByteArray? = null
        private set
    private var mFrameCount: Long = 0

    @SuppressLint("InlinedApi")
    private fun initEncoder() {
        LogContext.log.i(
            TAG,
            "initEncoder width=$width height=$height bitrate=$bitrate kb/s " +
                "frameRate=$frameRate bitrateMode=$bitrateMode iFrameInterval=$iFrameInterval"
        )

        val mediaFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width,
            height
        )
        with(mediaFormat) {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
            setInteger(MediaFormat.KEY_BITRATE_MODE, bitrateMode)

            // Traffic statistic Image in 1920X1080
            // Profile/Level                Traffic(KB/s)
            // 2130706434/65536(0x10000)    24K/s   (Only works in Samsung SM-9350)
            // 8/65536(0x10000)             76K/s
            // 2/65536(0x10000)             76K/s
            // 1/65536(0x10000)             76K/s
            val profileLevelPair =
                CodecUtil.getSupportedProfileLevelsForEncoder(MediaFormat.MIMETYPE_VIDEO_AVC)
                    // .firstOrNull {
                    //     it.profile ==
                    //         MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline
                    // }
                    // .firstOrNull {
                    //     it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
                    // }
                    // .firstOrNull {
                    //     it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileMain
                    // }
                    .firstOrNull {
                        it.profile ==
                            MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                    }
            //                .maxByOrNull { it.profile }
            val usedProfile =
                profileLevelPair?.profile ?: MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
            val usedLevel = profileLevelPair?.level ?: MediaCodecInfo.CodecProfileLevel.AVCLevel4

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                LogContext.log.w(TAG, "KEY_PROFILE: $usedProfile")
                setInteger(MediaFormat.KEY_PROFILE, usedProfile)
            } else {
                LogContext.log.w(TAG, "KEY_PROFILE static: AVCProfileBaseline")
                setInteger(
                    MediaFormat.KEY_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                )
            }
            //            setInteger(MediaFormat.KEY_COMPLEXITY, bitrateMode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // You must specify KEY_LEVEL on Android 6.0+
                // AVCLevel51
                // AVCLevel4
                LogContext.log.w(TAG, "KEY_LEVEL: $usedLevel")
                setInteger(MediaFormat.KEY_LEVEL, usedLevel)
            } else {
                LogContext.log.w(TAG, "KEY_LEVEL static: AVCLevel4")
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel4)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Actually, this key has been used in Android 6.0+ so you can use it safely only if
                // your device is Android 6.0+.
                // It's merely being opened as of Android 10.
                setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, frameRate.toFloat())
            }
        }

        val mediaCodecCallback = object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
                synchronized(inputBufferLock) {
                    availableInputBufferIds.addLast(inputBufferId)
                }
                drainInputBuffers(codec)
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                outputBufferId: Int,
                info: MediaCodec.BufferInfo
            ) {
                val encodedBytes = try {
                    codec.getOutputBuffer(outputBufferId)?.let { outputBuffer ->
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)
                        ByteArray(info.size).also(outputBuffer::get)
                    }
                } finally {
                    codec.releaseOutputBuffer(outputBufferId, false)
                }
                if (encodedBytes == null) return

                when (info.flags) {
                    MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> {
                        csd = encodedBytes.copyOf()
                        LogContext.log.w(TAG, "Found SPS/PPS frame: HEX[${csd?.toHexString()}]")
                    }

                    MediaCodec.BUFFER_FLAG_KEY_FRAME -> LogContext.log.i(
                        TAG,
                        "Found Key Frame[" + info.size + "]"
                    )
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM -> Unit
                    MediaCodec.BUFFER_FLAG_PARTIAL_FRAME -> Unit
                    else -> Unit
                }
                dataCallbackHandler?.post {
                    runCatching { dataUpdateCallback?.onCallback(encodedBytes) }
                        .onFailure { LogContext.log.e(TAG, "Encoded data callback failed.", it) }
                }
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                LogContext.log.w(TAG, "onOutputFormatChanged format=$format")
                // Subsequent data will conform to new format.
                // Can ignore if using getOutputFormat(outputBufferId)
                outputFormat = format // option B
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                LogContext.log.e(TAG, "onError e=${e.message}")
            }
        }

        //        h264Encoder = MediaCodec.createByCodecName("OMX.google.h264.encoder")
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val callbackThread = HandlerThread("$TAG-callback").apply { start() }
                codecCallbackThread = callbackThread
                val callbackHandler = Handler(callbackThread.looper)
                codecCallbackHandler = callbackHandler
                encoder.setCallback(mediaCodecCallback, callbackHandler)
            } else {
                encoder.setCallback(mediaCodecCallback)
            }
            val encodedDataThread = HandlerThread("$TAG-data-callback").apply { start() }
            dataCallbackThread = encodedDataThread
            dataCallbackHandler = Handler(encodedDataThread.looper)
            encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            h264Encoder = encoder
        } catch (e: Exception) {
            runCatching { encoder.release() }
            stopCodecCallbackThread()
            stopDataCallbackThread()
            throw e
        }
    }

    fun offerDataIntoQueue(data: ByteArray) {
        queue.offer(data)
        val encoder = if (::h264Encoder.isInitialized) h264Encoder else return
        codecCallbackHandler?.post { drainInputBuffers(encoder) } ?: drainInputBuffers(encoder)
    }

    private fun drainInputBuffers(codec: MediaCodec) {
        synchronized(inputBufferLock) {
            while (availableInputBufferIds.isNotEmpty()) {
                val data = queue.poll() ?: return
                val inputBufferId = availableInputBufferIds.removeFirst()
                try {
                    val inputBuffer = requireNotNull(codec.getInputBuffer(inputBufferId)) {
                        "MediaCodec returned a null input buffer for id=$inputBufferId"
                    }
                    inputBuffer.clear()
                    require(data.size <= inputBuffer.remaining()) {
                        "Frame size ${data.size} exceeds codec input capacity " +
                            inputBuffer.remaining()
                    }
                    inputBuffer.put(data)
                    val frameIndex = mFrameCount + 1
                    codec.queueInputBuffer(
                        inputBufferId,
                        0,
                        data.size,
                        computePresentationTimeUs(frameIndex),
                        0
                    )
                    mFrameCount = frameIndex
                } catch (e: Exception) {
                    LogContext.log.e(TAG, "Unable to queue codec input buffer.", e)
                }
            }
        }
    }

    private fun onEncoderFrameDropped() {
        val count = droppedFrameCount.incrementAndGet()
        if (count == 1L || count % DROPPED_FRAME_LOG_INTERVAL == 0L) {
            LogContext.log.w(TAG, "Encoder queue full, dropping frame (total dropped: $count)")
        }
    }

    fun setDataUpdateCallback(callback: CallbackListener?) {
        dataUpdateCallback = callback
    }

    private fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / frameRate

    @SuppressWarnings("unused")
    fun stop() {
        try {
            h264Encoder.stop()
        } catch (e: Exception) {
            LogContext.log.e(TAG, "stop() exception.")
        }
    }

    /**
     * Release sources.
     */
    fun release() {
        try {
            stop()
            h264Encoder.release()
        } finally {
            queue.clear()
            synchronized(inputBufferLock) { availableInputBufferIds.clear() }
            stopCodecCallbackThread()
            stopDataCallbackThread()
        }
    }

    private fun stopCodecCallbackThread() {
        val callbackThread = codecCallbackThread
        callbackThread?.quitSafely()
        if (callbackThread != null && Thread.currentThread() !== callbackThread) {
            runCatching { callbackThread.join(CALLBACK_THREAD_JOIN_TIMEOUT_MS) }
        }
        codecCallbackThread = null
        codecCallbackHandler = null
    }

    private fun stopDataCallbackThread() {
        val callbackThread = dataCallbackThread
        callbackThread?.quitSafely()
        if (callbackThread != null && Thread.currentThread() !== callbackThread) {
            runCatching { callbackThread.join(CALLBACK_THREAD_JOIN_TIMEOUT_MS) }
        }
        dataCallbackThread = null
        dataCallbackHandler = null
    }

    companion object {
        private const val TAG = "CameraEncoder"
        private const val MAX_PENDING_FRAMES = 5
        private const val DROPPED_FRAME_LOG_INTERVAL = 30L
        private const val CALLBACK_THREAD_JOIN_TIMEOUT_MS = 1_000L
        const val DEFAULT_KEY_I_FRAME_INTERVAL = 5
        const val DEFAULT_BITRATE_MODE = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
    }
}

internal class BoundedFrameQueue(
    private val capacity: Int,
    private val onElementDropped: () -> Unit = {}
) : ConcurrentLinkedQueue<ByteArray>() {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    override fun offer(element: ByteArray): Boolean {
        var droppedCount = 0
        val offered = synchronized(this) {
            while (size >= capacity) {
                if (super.poll() == null) break
                droppedCount++
            }
            super.offer(element)
        }
        repeat(droppedCount) { onElementDropped() }
        return offered
    }

    override fun addAll(elements: Collection<ByteArray>): Boolean {
        var changed = false
        elements.forEach { changed = offer(it) || changed }
        return changed
    }
}
