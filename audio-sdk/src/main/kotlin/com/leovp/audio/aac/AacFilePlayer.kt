package com.leovp.audio.aac

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.leovp.audio.AudioTrackPlayer
import com.leovp.audio.BuildConfig
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.log.LogContext
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Author: Michael Leo
 * Date: 2020/9/17 下午5:01
 */
class AacFilePlayer(
    ctx: Context,
    audioDecodeInfo: AudioDecoderInfo,
    usage: Int = AudioAttributes.USAGE_MEDIA, // AudioAttributes.USAGE_VOICE_COMMUNICATION  AudioAttributes.USAGE_MEDIA
    contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC, // AudioAttributes.CONTENT_TYPE_SPEECH  AudioAttributes.CONTENT_TYPE_MUSIC
) {
    companion object {
        private const val TAG = "AacFilePlayer"
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())

    private var isPlaying = false

    private val audioTrackPlayer: AudioTrackPlayer = AudioTrackPlayer(ctx, audioDecodeInfo, usage = usage, contentType = contentType)
    private var audioDecoder: MediaCodec? = null

    private var mediaExtractor: MediaExtractor? = null

    private fun initAudioDecoder(aacFile: File) {
        runCatching {
            mediaExtractor = MediaExtractor().apply { setDataSource(aacFile.absolutePath) }

            var mediaFormat: MediaFormat? = null
            var mime: String? = null
            for (i in 0 until mediaExtractor!!.trackCount) {
                val format = mediaExtractor?.getTrackFormat(i)
                mime = format?.getString(MediaFormat.KEY_MIME)
                if (mime!!.startsWith("audio/")) {
                    mediaExtractor?.selectTrack(i)
                    mediaFormat = format
                    break
                }
            }
            if (mediaFormat == null || mime.isNullOrBlank()) return

            // ByteBuffer key
            // AAC Profile 5bits | SampleRate 4bits | Channel Count 4bits | Others 3bits（Normally 0)
            // Example: AAC LC，44.1Khz，Mono. Separately values: 2，4，1.
            // Convert them to binary value: 0b10, 0b100, 0b1
            // According to AAC required, convert theirs values to binary bits:
            // 00010 0100 0001 000
            // The corresponding hex value：
            // 0001 0010 0000 1000
            // So the csd_0 value is 0x12,0x08
            // https://developer.android.com/reference/android/media/MediaCodec
            // AAC CSD: Decoder-specific information from ESDS

            // ByteBuffer key
            // AAC Profile 5bits | SampleRate 4bits | Channel Count 4bits | Others 3bits（Normally 0)
            // Example: AAC LC，44.1Khz，Mono. Separately values: 2，4，1.
            // Convert them to binary value: 0b10, 0b100, 0b1
            // According to AAC required, convert theirs values to binary bits:
            // 00010 0100 0001 000
            // The corresponding hex value：
            // 0001 0010 0000 1000
            // So the csd_0 value is 0x12,0x08

            // https://developer.android.com/reference/android/media/MediaCodec
            // AAC CSD: Decoder-specific information from ESDS
            //                mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, audioDecodeInfo.sampleRate, audioDecodeInfo.channelCount)
            //                mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))

            audioDecoder = MediaCodec.createDecoderByType(mime).apply {
                configure(mediaFormat, null, null, 0)
                start()
            }
        }.onFailure { it.printStackTrace() }
    }

    fun playAac(aacFile: File, f: () -> Unit) {
        isPlaying = true
        initAudioDecoder(aacFile)
        audioTrackPlayer.play()
        ioScope.launch {
            try {
                var isFinish = false
                while (!isFinish && isPlaying) {
                    val inputIndex = audioDecoder?.dequeueInputBuffer(0)!!
                    // if (BuildConfig.DEBUG) LogContext.log.v(TAG, "inputIndex=$inputIndex")
                    if (inputIndex > -1) {
                        var sampleSize = -1
                        var sampleData: ByteArray?
                        try {
                            audioDecoder?.getInputBuffer(inputIndex)?.let {
                                it.clear()
                                sampleSize = mediaExtractor?.readSampleData(it, 0) ?: -1
                                if (sampleSize < 0) {
                                    isFinish = true
                                } else {
                                    sampleData = ByteArray(it.remaining())
                                    it.get(sampleData!!)
                                    // if (BuildConfig.DEBUG) LogContext.log.d(TAG, "Sample aac data[${sampleData?.size}]")
                                }
                            }
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) LogContext.log.e(TAG, "inputIndex=$inputIndex sampleSize=$sampleSize", e)
                        }
                        // if (BuildConfig.DEBUG) LogContext.log.v(TAG, "sampleSize=$sampleSize")
                        if (sampleSize < 0) {
                            audioDecoder?.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isFinish = true
                        } else {
                            audioDecoder?.queueInputBuffer(inputIndex, 0, sampleSize, mediaExtractor?.sampleTime ?: 0, 0)
                            mediaExtractor?.advance()
                        }
                    }

                    val decodeBufferInfo = MediaCodec.BufferInfo()
                    var outputIndex: Int = audioDecoder?.dequeueOutputBuffer(decodeBufferInfo, 0) ?: -1
                    // if (BuildConfig.DEBUG) LogContext.log.v(TAG, "outputIndex=$outputIndex")
                    var chunkPCM: ByteArray
                    while (outputIndex >= 0) {
                        chunkPCM = ByteArray(decodeBufferInfo.size)
                        audioDecoder?.getOutputBuffer(outputIndex)?.apply {
                            position(decodeBufferInfo.offset)
                            limit(decodeBufferInfo.offset + decodeBufferInfo.size)
                            get(chunkPCM)
                            clear()
                        }
                        if (chunkPCM.isNotEmpty()) {
                            LogContext.log.i(TAG, "PCM data[${chunkPCM.size}]")
                            audioTrackPlayer.write(chunkPCM)
                        }
                        audioDecoder?.releaseOutputBuffer(outputIndex, false)
                        outputIndex = audioDecoder?.dequeueOutputBuffer(decodeBufferInfo, 0) ?: -1
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) LogContext.log.e(TAG, "playAac() error", e)
            } finally {
                stop()
                f.invoke()
            }
        }
    }

    fun stop() {
        isPlaying = false
        audioTrackPlayer.release()
        runCatching {
            audioDecoder?.release()
        }.onFailure { it.printStackTrace() }
        runCatching { mediaExtractor?.release() }.onFailure { it.printStackTrace() }
    }
}
