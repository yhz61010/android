package com.ho1ho.audio.player.aac

import android.content.Context
import android.media.*
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.audio.base.AudioCodecInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * Author: Michael Leo
 * Date: 2020/9/17 下午5:01
 */
class AacFilePlayer(private val ctx: Context, private val audioDecodeInfo: AudioCodecInfo) {
    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private var audioManager: AudioManager? = null

    private var isPlaying = false

    private var audioTrack: AudioTrack? = null
    private var audioDecoder: MediaCodec? = null

    private lateinit var mediaExtractor: MediaExtractor

    private fun initAudioDecoder(aacFile: File, csd0: ByteArray) {
        runCatching {
            mediaExtractor = MediaExtractor()
            mediaExtractor.setDataSource(aacFile.absolutePath)

            var format = mediaExtractor.getTrackFormat(0)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            if (mime.startsWith("audio")) {
                mediaExtractor.selectTrack(0)
                audioDecoder = MediaCodec.createDecoderByType(mime)
                format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, audioDecodeInfo.sampleRate, audioDecodeInfo.channelCount)

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
                format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
                audioDecoder?.configure(format, null, null, 0)
            } else {
                return
            }
        }.onFailure { it.printStackTrace() }
        audioDecoder?.start()
    }

    private fun initAudioTrack(ctx: Context, audioData: AudioCodecInfo) {
        runCatching {
            audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val bufferSize = AudioTrack.getMinBufferSize(audioData.sampleRate, audioData.channelConfig, audioData.audioFormat)
            val sessionId = audioManager!!.generateAudioSessionId()
            val audioAttributesBuilder = AudioAttributes.Builder().apply {
                // Speaker
                setUsage(AudioAttributes.USAGE_MEDIA)              // AudioAttributes.USAGE_MEDIA         AudioAttributes.USAGE_VOICE_COMMUNICATION
                setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) // AudioAttributes.CONTENT_TYPE_MUSIC   AudioAttributes.CONTENT_TYPE_SPEECH
                setLegacyStreamType(AudioManager.STREAM_MUSIC)
            }
            val audioFormat = AudioFormat.Builder().setSampleRate(audioData.sampleRate)
                .setEncoding(audioData.audioFormat)
                .setChannelMask(audioData.channelConfig)
                .build()
            audioTrack = AudioTrack(audioAttributesBuilder.build(), audioFormat, bufferSize, AudioTrack.MODE_STREAM, sessionId)

            if (AudioTrack.STATE_INITIALIZED == audioTrack!!.state) {
                LLog.w(ITAG, "Start playing audio...")
                audioTrack!!.play()
            } else {
                LLog.w(ITAG, "AudioTrack state is not STATE_INITIALIZED")
            }
        }.onFailure { LLog.e(ITAG, "initAudioTrack error msg=${it.message}") }
    }

    fun playAac(aacFile: File, f: () -> Unit) {
        isPlaying = true

        val csd0WithAdts = ByteArray(9)
        val csd0 = ByteArray(2)
        BufferedInputStream(FileInputStream(aacFile)).use { input ->
            input.read(csd0WithAdts, 0, 9)
            csd0[0] = csd0WithAdts[7]
            csd0[1] = csd0WithAdts[8]
        }

        initAudioDecoder(aacFile, csd0)
        initAudioTrack(ctx, audioDecodeInfo)
        ioScope.launch {
            try {
                var isFinish = false
                val decodeBufferInfo = MediaCodec.BufferInfo()
                while (!isFinish && isPlaying) {
                    val inputIndex = audioDecoder?.dequeueInputBuffer(0)!!
                    LLog.w(ITAG, "inputIndex=$inputIndex")
                    if (inputIndex < 0) {
                        isFinish = true
                    }
                    var sampleSize = -1
                    var sampleData: ByteArray?
                    audioDecoder?.getInputBuffer(inputIndex)?.let {
                        it.clear()
                        sampleSize = mediaExtractor.readSampleData(it, 0)
                        sampleData = ByteArray(it.remaining())
                        it.get(sampleData!!)
                        LLog.i(ITAG, "Sample aac data[${sampleData?.size}]")
                    }

                    if (sampleSize > 0) {
                        audioDecoder?.queueInputBuffer(inputIndex, 0, sampleSize, 0 /*mediaExtractor.getSampleTime()*/, 0)
                        mediaExtractor.advance()
                    } else {
                        audioDecoder?.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isFinish = true
                    }
                    var outputIndex: Int = audioDecoder?.dequeueOutputBuffer(decodeBufferInfo, 0) ?: -1
                    LLog.e(ITAG, "outputIndex=$outputIndex")
                    var outputBuffer: ByteBuffer?
                    var chunkPCM: ByteArray
                    while (outputIndex >= 0) {
                        chunkPCM = ByteArray(decodeBufferInfo.size)
                        outputBuffer = audioDecoder?.getOutputBuffer(outputIndex)
                        outputBuffer?.get(chunkPCM)
                        outputBuffer?.clear()
                        if (chunkPCM.isNotEmpty()) {
//                                LLog.i(TAG, "PCM data[" + chunkPCM.length + "]=" + Arrays.toString(chunkPCM));
                            val shortPcmData = ShortArray(chunkPCM.size / 2)
                            for (i in shortPcmData.indices) {
                                shortPcmData[i] = (chunkPCM[i * 2].toInt() and 0xFF or (chunkPCM[i * 2 + 1].toInt() shl 8)).toShort()
                            }
                            LLog.i(ITAG, "Finally PCM data[${shortPcmData.size}]")
                            audioTrack?.write(shortPcmData, 0, shortPcmData.size)
                        }
                        audioDecoder?.releaseOutputBuffer(outputIndex, false)
                        outputIndex = audioDecoder?.dequeueOutputBuffer(decodeBufferInfo, 0) ?: -1
                    }
                }
                f.invoke()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()

        }.onFailure { it.printStackTrace() }
        runCatching {
            audioDecoder?.stop()
            audioDecoder?.release()
        }.onFailure { it.printStackTrace() }
    }
}