package com.ho1ho.mediacodec_sdk.h265

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue


/**
 * Author: Michael Leo
 * Date: 20-7-27 上午11:26
 */
class HEVCDecoder {
    private var decoder: MediaCodec? = null
    private var outputFormat: MediaFormat? = null

    private var frameCount: Long = 0
    val queue = ConcurrentLinkedQueue<ByteArray>()

    fun initDecoder(surface: Surface, csd0: ByteArray, width: Int, height: Int) {
        decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
//        decoder = MediaCodec.createByCodecName("OMX.google.hevc.decoder")
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
        decoder?.configure(format, surface, null, 0)
        outputFormat = decoder?.outputFormat // option B
        decoder?.setCallback(mediaCodecCallback)
        decoder?.start()
    }

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
            try {
                val inputBuffer = codec.getInputBuffer(inputBufferId)
                // fill inputBuffer with valid data
                inputBuffer?.clear()
                val data = queue.poll()?.also {
//                CLog.i(ITAG, "onInputBufferAvailable length=${it.size}")
                    inputBuffer?.put(it)
                }
                codec.queueInputBuffer(inputBufferId, 0, data?.size ?: 0, computePresentationTimeUs(++frameCount), 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, outputBufferId: Int, info: MediaCodec.BufferInfo) {
            val outputBuffer = codec.getOutputBuffer(outputBufferId)
            // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
            // bufferFormat is equivalent to member variable outputFormat
            // outputBuffer is ready to be processed or rendered.
            outputBuffer?.let {
                val decodedData = ByteArray(info.size)
                it.get(decodedData)
//                CLog.i(ITAG, "onOutputBufferAvailable length=${info.size}")
                when (info.flags) {
                    MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> {
                        LLog.w(ITAG, "Found VPS/SPS/PPS frame: ${decodedData.contentToString()}")
                    }
                    MediaCodec.BUFFER_FLAG_KEY_FRAME -> LLog.i(ITAG, "Found Key Frame[" + info.size + "]")
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM -> {
                        // Do nothing
                    }
                    MediaCodec.BUFFER_FLAG_PARTIAL_FRAME -> {
                        // Do nothing
                    }
                    else -> {
                        // Do nothing
                    }
                }
            }
            codec.releaseOutputBuffer(outputBufferId, true)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            LLog.w(ITAG, "onOutputFormatChanged format=$format")
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            outputFormat = format // option B
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            LLog.e(ITAG, "onError e=${e.message}")
        }
    }

    private fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / 20

    fun getVpsSpsPps(data: ByteArray, offset: Int, length: Int): ByteArray? {
        var i = 0
        var vps = -1
        var sps = -1
        var pps = -1
        do {
            if (vps == -1) {
                i = offset
                while (i < length - 4) {
                    if (0x00 == data[i].toInt() && 0x00 == data[i + 1].toInt() && 0x00 == data[i + 2].toInt() && 0x01 == data[i + 3].toInt()) {
                        val nal_spec = data[i + 4]
                        val nal_type: Int = nal_spec.toInt() shr 1 and 0x03f
                        if (nal_type == NAL_VPS) {
                            // vps found.
                            vps = if (data[i - 1].toInt() == 0x00) {  // start with 00 00 00 01
                                i - 1
                            } else {                      // start with 00 00 01
                                i
                            }
                            break
                        }
                    }
                    i++
                }
            }
            if (sps == -1) {
                i = vps
                while (i < length - 4) {
                    if (0x00 == data[i].toInt() && 0x00 == data[i + 1].toInt() && 0x00 == data[i + 2].toInt() && 0x01 == data[i + 3].toInt()) {
                        val nal_spec = data[i + 4]
                        val nal_type: Int = nal_spec.toInt() shr 1 and 0x03f
                        if (nal_type == NAL_SPS) {
                            // vps found.
                            sps = if (data[i - 1].toInt() == 0x00) {  // start with 00 00 00 01
                                i - 1
                            } else {                      // start with 00 00 01
                                i
                            }
                            break
                        }
                    }
                    i++
                }
            }
            if (pps == -1) {
                i = sps
                while (i < length - 4) {
                    if (0x00 == data[i].toInt() && 0x00 == data[i + 1].toInt() && 0x00 == data[i + 2].toInt() && 0x01 == data[i + 3].toInt()) {
                        val nal_spec = data[i + 4]
                        val nal_type: Int = nal_spec.toInt() shr 1 and 0x03f
                        if (nal_type == NAL_PPS) {
                            // vps found.
                            pps = if (data[i - 1].toInt() == 0x00) {  // start with 00 00 00 01
                                i - 1
                            } else {                    // start with 00 00 01
                                i
                            }
                            break
                        }
                    }
                    i++
                }
            }
        } while (vps == -1 || sps == -1 || pps == -1)
        if (vps == -1 || sps == -1 || pps == -1) { // 没有获取成功。
            return null
        }
        // 计算csd buffer的长度。即从vps的开始到pps的结束的一段数据
        val begin = vps
        var end = -1
        i = pps
        while (i < length - 4) {
            if (0x00 == data[i].toInt() && 0x00 == data[i + 1].toInt() && 0x00 == data[i + 2].toInt() && 0x01 == data[i + 3].toInt()) {
                end = if (data[i - 1].toInt() == 0x00) {  // start with 00 00 00 01
                    i - 1
                } else {                    // start with 00 00 01
                    i
                }
                break
            }
            i++
        }
        if (end == -1 || end < begin) {
            return null
        }
        // 拷贝并返回
        val buf = ByteArray(end - begin)
        System.arraycopy(data, begin, buf, 0, buf.size)
        return buf
    }

    companion object {
        private const val NAL_VPS = 32
        private const val NAL_SPS = 33
        private const val NAL_PPS = 34
    }
}