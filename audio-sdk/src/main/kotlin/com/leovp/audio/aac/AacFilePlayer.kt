package com.leovp.audio.aac

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.leovp.audio.AudioTrackPlayer
import com.leovp.audio.base.bean.AudioDecoderInfo
import com.leovp.audio.mediacodec.BaseMediaCodecSynchronous
import com.leovp.bytes.toByteArray
import com.leovp.log.LogContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Author: Michael Leo
 * Date: 2020/9/17 下午5:01
 */
class AacFilePlayer(
    ctx: Context,
    audioDecodeInfo: AudioDecoderInfo,
    // AudioAttributes.USAGE_VOICE_COMMUNICATION  AudioAttributes.USAGE_MEDIA
    usage: Int = AudioAttributes.USAGE_MEDIA,
    // AudioAttributes.CONTENT_TYPE_SPEECH  AudioAttributes.CONTENT_TYPE_MUSIC
    contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC
) : BaseMediaCodecSynchronous(MediaFormat.MIMETYPE_AUDIO_AAC, audioDecodeInfo.sampleRate, audioDecodeInfo.channelCount) {
    companion object {
        private const val TAG = "AacFilePlayer"
    }

    private val audioTrackPlayer: AudioTrackPlayer = AudioTrackPlayer(ctx, audioDecodeInfo, usage = usage, contentType = contentType)

    private var mediaFormat: MediaFormat? = null
    private var mime: String? = null
    private var mediaExtractor: MediaExtractor? = null

    private var cb: (() -> Unit)? = null

    override fun setFormatOptions(format: MediaFormat) {}

    override fun createMediaFormat() {}

    override fun createCodec() {
        codec = MediaCodec.createDecoderByType(mime!!)
        codec.configure(mediaFormat, null, null, 0)
        // LogContext.log.w(TAG, "mediaFormat=$mediaFormat")
    }

    override fun onInputData(inBuf: ByteBuffer): Int {
        val sampleSize = mediaExtractor?.readSampleData(inBuf, 0) ?: -1
        if (sampleSize > -1) {
            mediaExtractor?.advance()
        } else {
            LogContext.log.d(TAG, "readSampleData sampleSize=$sampleSize")
        }
        return sampleSize
    }

    override fun onOutputData(outBuf: ByteBuffer, info: MediaCodec.BufferInfo, isConfig: Boolean, isKeyFrame: Boolean) {
        // LogContext.log.e(TAG, "onOutputData isConfig=$isConfig isKeyFrame=$isKeyFrame")
        val chunkPCM = outBuf.toByteArray()
        if (chunkPCM.isNotEmpty()) {
            LogContext.log.i(TAG, "PCM data[${chunkPCM.size}] isConfig=$isConfig isKeyFrame=$isKeyFrame")
            audioTrackPlayer.write(chunkPCM)
        }
    }

    override fun computePresentationTimeUs(): Long {
        return mediaExtractor?.sampleTime ?: -1
    }

    override fun onEndOfStream() {
        cb?.invoke()
    }

    fun playAac(aacFile: File, endCallback: () -> Unit) {
        cb = endCallback
        audioTrackPlayer.play()
        runCatching {
            mediaExtractor = MediaExtractor().apply { setDataSource(aacFile.absolutePath) }
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
            start()
        }.onFailure { it.printStackTrace() }
    }

    override fun stop() {
        if (audioTrackPlayer.state == AudioTrack.STATE_UNINITIALIZED) {
            return
        }
        runCatching { mediaExtractor?.release() }.onFailure { it.printStackTrace() }
        audioTrackPlayer.release()
        super.release()
    }
}
