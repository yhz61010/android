package com.leovp.audio.adpcm

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacpp.BytePointer

class AdpcmImaQTDecoder(private val channel: Int, sampleRate: Int) {
    init {
        init(channel, sampleRate)
    }

    private var ctx: AVCodecContext? = null

    private fun init(channel: Int, sampleRate: Int): Boolean {
        val codec = avcodec.avcodec_find_decoder(avcodec.AV_CODEC_ID_ADPCM_IMA_QT)
        ctx = avcodec.avcodec_alloc_context3(codec).apply {
            channels(channel)
            sample_rate(sampleRate)
            channel_layout(avutil.av_get_default_channel_layout(channel))
        }

        return avcodec.avcodec_open2(ctx, codec, null as AVDictionary?) >= 0
    }

    /**
     * In QuickTime, IMA is encoded by chunks of 34 bytes (=64 samples).
     * Channel data is interleaved per-chunk.
     */
    fun chunkSize(): Int {
        return 34 * channel
    }

    var pts: Long = 0
        private set

    fun decode(pcm: ByteArray): AVFrame? {
        if (pcm.size != chunkSize()) {
            throw RuntimeException("Invalid ChunkSize: ${pcm.size}, required: ${chunkSize()}, In QuickTime, IMA is encoded by chunks of 34 bytes (=64 samples)")
        }
        var rtnCode: Int
        val pkt = avcodec.av_packet_alloc()
        if (avcodec.av_new_packet(pkt, pcm.size).also { rtnCode = it } < 0) {
            return null
        }
        try {
            BytePointer(*pcm).use { p ->
                pkt.data(p)
                pkt.pts(pcm.size.let { pts += it; pts })
                rtnCode = avcodec.avcodec_send_packet(ctx, pkt)
                if (rtnCode < 0) {
                    avcodec.av_packet_free(pkt)
                    return null
                }
                val frame = avutil.av_frame_alloc()
                if (avcodec.avcodec_receive_frame(ctx, frame).also { rtnCode = it } < 0) {
                    avutil.av_frame_free(frame)
                    return null
                }
                return frame
            }
        } finally {
            avcodec.av_packet_free(pkt)
            pkt.close()
        }
    }

    fun close() {
        if (ctx != null) {
            avcodec.avcodec_free_context(ctx)
            ctx = null
        }
    }

    companion object {
        fun getSampleFormatName(fmt: Int): String? = avutil.av_get_sample_fmt_name(fmt).use { ptr -> return if (ptr != null && !ptr.isNull) ptr.string else null }

        fun isAvSampleInPlanar(fmt: Int): Boolean = avutil.av_sample_fmt_is_planar(fmt) == 1
    }
}