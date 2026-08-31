@file:Suppress("unused")

package com.leovp.ffmpeg.javacpp.audio.adpcm

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacpp.BytePointer
import java.io.Closeable

class AdpcmImaQTDecoder(sampleRate: Int, private val channel: Int) : Closeable {
    private var context: AVCodecContext? = null
    private var packet: AVPacket? = null
    private var frame: AVFrame? = null

    init {
        require(sampleRate > 0) { "Sample rate must be positive." }
        require(channel == 1 || channel == 2) { "Channel count must be 1 or 2." }
        initialize(sampleRate)
    }

    private fun initialize(sampleRate: Int) {
        try {
            val codec = avcodec.avcodec_find_decoder(avcodec.AV_CODEC_ID_ADPCM_IMA_QT)
            check(codec != null && !codec.isNull) { "ADPCM IMA QT decoder was not found." }
            context = avcodec.avcodec_alloc_context3(codec)
            val initializedContext = context
            check(initializedContext != null && !initializedContext.isNull) {
                "Unable to allocate the ADPCM codec context."
            }
            initializedContext.ch_layout(
                if (channel == 1) {
                    avutil.AV_CHANNEL_LAYOUT_MONO
                } else {
                    avutil.AV_CHANNEL_LAYOUT_STEREO
                }
            )
            initializedContext.sample_rate(sampleRate)
            check(avcodec.avcodec_open2(initializedContext, codec, null as AVDictionary?) >= 0) {
                "Unable to open the ADPCM codec."
            }

            packet = avcodec.av_packet_alloc()
            check(packet != null && packet?.isNull == false) { "Unable to allocate an AVPacket." }
            frame = avutil.av_frame_alloc()
            check(frame != null && frame?.isNull == false) { "Unable to allocate an AVFrame." }
        } catch (throwable: Throwable) {
            releaseResources()
            throw throwable
        }
    }

    /** In QuickTime, one IMA chunk contains 34 bytes and decodes to 64 samples per channel. */
    fun chunkSize(): Int = ENCODED_CHUNKS_SIZE * channel

    /**
     * Decodes one ADPCM IMA QT chunk into separate PCM channel buffers.
     *
     * Mono output is returned as `leftBytes to ByteArray(0)`. A codec rejection returns `null`.
     */
    @Synchronized
    fun decode(adpcmBytes: ByteArray): Pair<ByteArray, ByteArray>? {
        require(adpcmBytes.size == chunkSize()) {
            "Invalid ChunkSize: ${adpcmBytes.size}, required: ${chunkSize()}, " +
                "In QuickTime, IMA is encoded by chunks of 34*channels bytes (=64 samples)"
        }
        val initializedContext = checkNotNull(context) { "Decoder is closed." }
        val reusablePacket = checkNotNull(packet) { "Decoder is closed." }
        val reusableFrame = checkNotNull(frame) { "Decoder is closed." }

        avcodec.av_packet_unref(reusablePacket)
        avutil.av_frame_unref(reusableFrame)
        check(avcodec.av_new_packet(reusablePacket, adpcmBytes.size) >= 0) {
            "Unable to allocate the ADPCM packet payload."
        }
        try {
            val packetData = reusablePacket.data()
            check(packetData != null && !packetData.isNull) { "ADPCM packet has no payload." }
            packetData.position(0).put(adpcmBytes, 0, adpcmBytes.size)
            packetData.position(adpcmBytes.size.toLong()).put(INPUT_PADDING, 0, INPUT_PADDING.size)
            packetData.position(0)

            if (
                avcodec.avcodec_send_packet(initializedContext, reusablePacket) < 0 ||
                avcodec.avcodec_receive_frame(initializedContext, reusableFrame) < 0
            ) {
                return null
            }
            return copyDecodedChannels(reusableFrame)
        } finally {
            avutil.av_frame_unref(reusableFrame)
            avcodec.av_packet_unref(reusablePacket)
        }
    }

    private fun copyDecodedChannels(decodedFrame: AVFrame): Pair<ByteArray, ByteArray> {
        val bytesPerSample = avutil.av_get_bytes_per_sample(decodedFrame.format())
        check(bytesPerSample > 0 && decodedFrame.nb_samples() > 0) {
            "Decoder returned an invalid sample format or sample count."
        }
        val decodedChannels = decodedFrame.ch_layout().nb_channels()
        check(decodedChannels == channel) { "Decoder returned an unexpected channel count." }
        val bytesPerChannel = Math.multiplyExact(decodedFrame.nb_samples(), bytesPerSample)
        val planar = avutil.av_sample_fmt_is_planar(decodedFrame.format()) == 1

        if (planar) {
            val left = readBytes(decodedFrame.extended_data(0), bytesPerChannel)
            val right = if (decodedChannels == 2) {
                readBytes(decodedFrame.extended_data(1), bytesPerChannel)
            } else {
                ByteArray(0)
            }
            return left to right
        }

        val packedSize = Math.multiplyExact(bytesPerChannel, decodedChannels)
        val packed = readBytes(decodedFrame.extended_data(0), packedSize)
        if (decodedChannels == 1) return packed to ByteArray(0)

        val left = ByteArray(bytesPerChannel)
        val right = ByteArray(bytesPerChannel)
        repeat(decodedFrame.nb_samples()) { sampleIndex ->
            val packedOffset = sampleIndex * decodedChannels * bytesPerSample
            val channelOffset = sampleIndex * bytesPerSample
            packed.copyInto(left, channelOffset, packedOffset, packedOffset + bytesPerSample)
            packed.copyInto(
                right,
                channelOffset,
                packedOffset + bytesPerSample,
                packedOffset + bytesPerSample * 2
            )
        }
        return left to right
    }

    private fun readBytes(pointer: BytePointer?, length: Int): ByteArray {
        check(pointer != null && !pointer.isNull) { "Decoder returned a null audio plane." }
        return ByteArray(length).also { pointer.position(0).get(it) }
    }

    @Synchronized
    override fun close() {
        releaseResources()
    }

    private fun releaseResources() {
        frame?.let(avutil::av_frame_free)
        frame = null
        packet?.let(avcodec::av_packet_free)
        packet = null
        context?.let(avcodec::avcodec_free_context)
        context = null
    }

    companion object {
        private const val ENCODED_CHUNKS_SIZE = 34
        private val INPUT_PADDING = ByteArray(avcodec.AV_INPUT_BUFFER_PADDING_SIZE)

        fun getSampleFormatName(fmt: Int): String? = avutil.av_get_sample_fmt_name(fmt).let { ptr ->
            return if (ptr != null && !ptr.isNull) ptr.string else null
        }

        fun isAvSampleInPlanar(fmt: Int): Boolean = avutil.av_sample_fmt_is_planar(fmt) == 1
    }
}
