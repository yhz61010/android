@file:Suppress("unused")

package com.leovp.ffmpeg.javacpp.audio.adpcm

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacpp.BytePointer

class AdpcmImaQTDecoder(sampleRate: Int, private val channel: Int) {
    init {
        init(sampleRate, channel)
    }

    private var ctx: AVCodecContext? = null

    private fun init(sampleRate: Int, channel: Int): Boolean {
        val codec = avcodec.avcodec_find_decoder(avcodec.AV_CODEC_ID_ADPCM_IMA_QT)
        ctx = avcodec.avcodec_alloc_context3(codec).apply {
            val ch = if (channel == 1) {
                avutil.AV_CHANNEL_LAYOUT_MONO
            } else {
                avutil.AV_CHANNEL_LAYOUT_STEREO
            }
            ch_layout(ch)
            sample_rate(sampleRate)
            // Old version
            // channels(channel)
            // channel_layout(avutil.av_get_default_channel_layout(channel))
        }

        return avcodec.avcodec_open2(ctx, codec, null as AVDictionary?) >= 0
    }

    /**
     * In QuickTime, IMA is encoded by chunks of 34 bytes (=64 samples).
     * Channel data is interleaved per-chunk.
     */
    fun chunkSize(): Int = ENCODED_CHUNKS_SIZE * channel

    fun decode(adpcmBytes: ByteArray): Pair<ByteArray, ByteArray>? {
        if (adpcmBytes.size != chunkSize()) {
            throw IllegalArgumentException(
                "Invalid ChunkSize: ${adpcmBytes.size}, required: ${chunkSize()}, " +
                    "In QuickTime, IMA is encoded by chunks of 34*channels bytes (=64 samples)"
            )
        }
        val pkt = avcodec.av_packet_alloc()
        try {
            val p = BytePointer(*adpcmBytes)
            pkt.data(p)
            pkt.size(adpcmBytes.size)
            val rtnCodeForSend = avcodec.avcodec_send_packet(ctx, pkt)
            val frame = avutil.av_frame_alloc()
            val rtnCodeForReceive = avcodec.avcodec_receive_frame(ctx, frame)
            if (rtnCodeForSend < 0 || rtnCodeForReceive < 0) {
                avutil.av_frame_free(frame)
                avcodec.av_packet_free(pkt)
                return null
            }

            // if (LogContext.enableLog) LogContext.log.i(
            //     "bytes per sample=${avutil.av_get_bytes_per_sample(frame.format())} ch:${frame.channels()} " +
            //         "sampleRate:${frame.sample_rate()} np_samples:${frame.nb_samples()} " +
            //         " linesize[0]=${frame.linesize(0)} fmt[${frame.format()}]:${getSampleFormatName(frame.format())}"
            // )

            val bpLeft: BytePointer = frame.extended_data(0)
            val leftChunkBytes = ByteArray(frame.linesize(0))
            bpLeft.get(leftChunkBytes)

            val bpRight: BytePointer = frame.extended_data(1)
            // "0" is not typo. Check its document.
            val rightChunkBytes = ByteArray(frame.linesize(0))
            bpRight.get(rightChunkBytes)

            return leftChunkBytes to rightChunkBytes
        } finally {
            pkt.close()
            avcodec.av_packet_free(pkt)
        }
    }

    fun close() {
        if (ctx != null) {
            avcodec.avcodec_free_context(ctx)
            ctx = null
        }
    }

    companion object {
        private const val ENCODED_CHUNKS_SIZE = 34

        fun getSampleFormatName(fmt: Int): String? = avutil.av_get_sample_fmt_name(fmt).let { ptr ->
            return if (ptr != null && !ptr.isNull) ptr.string else null
        }

        fun isAvSampleInPlanar(fmt: Int): Boolean = avutil.av_sample_fmt_is_planar(fmt) == 1
    }
}
