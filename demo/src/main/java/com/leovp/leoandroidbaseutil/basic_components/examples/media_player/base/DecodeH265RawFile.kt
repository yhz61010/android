package com.leovp.leoandroidbaseutil.basic_components.examples.media_player.base

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.leovp.androidbase.exts.ITAG
import com.leovp.androidbase.exts.kotlin.toHexStringLE
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.ui.ToastUtil
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue


/**
 * [H265 NAL Unit Header](https://tools.ietf.org/html/rfc7798#page-13)
 *
 * ```plain
 * +---------------+---------------+
 * |0|1|2|3|4|5|6|7|0|1|2|3|4|5|6|7|
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |F|   Type    |  LayerId  | TID |
 * +-------------+-----------------+
 * ```
 *
 * - F: 1 bit
 *
 * forbidden_zero_bit.  Required to be zero in HEVC.  Note that the
 * inclusion of this bit in the NAL unit header was to enable
 * transport of HEVC video over MPEG-2 transport systems (avoidance
 * of start code emulations) MPEG2S.  In the context of this memo,
 * the value 1 may be used to indicate a syntax violation, e.g., for
 * a NAL unit resulted from aggregating a number of fragmented units
 * of a NAL unit but missing the last fragment, as described in Section 4.4.3.
 *
 * - Type: 6 bits
 *
 * nal_unit_type.  This field specifies the NAL unit type as defined
 * in Table 7-1 of HEVC.  If the most significant bit of this field
 * of a NAL unit is equal to 0 (i.e., the value of this field is less
 * than 32), the NAL unit is a VCL NAL unit.  Otherwise, the NAL unit
 * is a non-VCL NAL unit.  For a reference of all currently defined
 * NAL unit types and their semantics, please refer to Section 7.4.2
 * in HEVC.
 *
 * - LayerId: 6 bits
 *
 * nuh_layer_id.  Required to be equal to zero in HEVC.  It is
 * anticipated that in future scalable or 3D video coding extensions
 * of this specification, this syntax element will be used to
 * identify additional layers that may be present in the CVS, wherein
 * a layer may be, e.g., a spatial scalable layer, a quality scalable
 * layer, a texture view, or a depth view.
 *
 * - TID: 3 bits
 *
 * nuh_temporal_id_plus1.  This field specifies the temporal
 * identifier of the NAL unit plus 1.  The value of TemporalId is
 * equal to TID minus 1.  A TID value of 0 is illegal to ensure that
 * there is at least one bit in the NAL unit header equal to 1, so to
 * enable independent considerations of start code emulations in the
 * NAL unit header and in the NAL unit payload data.
 *
 * ```hexadecimal
 * 00 00 00 01 40 01  nal_unit_type 值为 32， VPS 语义为视频参数集
 * 00 00 00 01 42 01  nal_unit_type 值为 33， SPS 语义为序列参数集
 * 00 00 00 01 44 01  nal_unit_type 值为 34， PPS 语义为图像参数集
 * 00 00 00 01 4E 01  nal_unit_type 值为 39， SEI 语义为补充增强信息
 * 00 00 00 01 26 01  nal_unit_type 值为 19， IDR 语义为可能有 RADL 图像的 IDR 图像的 SS(Slice Segment) 编码数据
 * 00 00 00 01 02 01  nal_unit_type 值为  1， P   语义为被参考的后置图像，且非 TSA、非 STSA 的 SS(Slice Segment) 编码数据
 * ```
 *
 * Example:
 * 0x40 0x01 = 0b 0100 0000 0000 0001
 * ```plain
 * +---------------+---------------+
 * |0|1|2|3|4|5|6|7|0|1|2|3|4|5|6|7|
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |F|   Type    |  LayerId  | TID |
 * |0|1 0 0 0 0 0|0 0 0 0 0 0|0 0 1|
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * ```
 * The NALU type is 0b0010_0000 = 0x20 = 32
 * or you can calculate it like this:
 * Example:
 * (0x40 & 0x7E) >> 1
 *   0b0100_0000
 * & 0b0111_1110
 * --------------
 *   0b0100_0000 >> 1 = 0b0010_0000 = 0x20 = 32
 *
 * or you can also calculate it like this:
 * Example:
 * (0x40 >> 1) & 0x3F
 *   0b0010_0000
 * & 0b0011_1111
 * --------------
 *   0b0010_0000 = 0x20 = 32
 *
 * So the full NALU header value are:
 * F=0
 * Type=32
 * LayerId=0
 * TID=1
 *
 * According to some articles(Not verified), NALU type from 0~9 indicates P frame, 16~21 indicates I frame.
 *
 * Author: Michael Leo
 * Date: 20-7-30 上午10:54
 */
class DecodeH265RawFile {
    companion object {
        private const val TAG = "DecodeH265RawFile"
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())

    private lateinit var mediaCodec: MediaCodec
    private var outputFormat: MediaFormat? = null
    private var frameCount: Long = 0

    private val queue = ArrayBlockingQueue<ByteArray>(10)
    private var csd0Size: Int = 0

    fun init(videoFile: String, width: Int, height: Int, surface: Surface) {
        runCatching {
            rf = RandomAccessFile(File(videoFile), "r")
            LogContext.log.w(TAG, "File length=${rf.length()}")

            val vps = getNalu()!!
            val sps = getNalu()!!
            val pps = getNalu()!!

            LogContext.log.w(TAG, "vps[${vps.size}]=${vps.toHexStringLE()}")
            LogContext.log.w(TAG, "sps[${sps.size}]=${sps.toHexStringLE()}")
            LogContext.log.w(TAG, "pps[${pps.size}]=${pps.toHexStringLE()}")

            val csd0 = vps + sps + pps
            LogContext.log.w(TAG, "csd0[${csd0.size}]=${csd0.toHexStringLE()}")
            csd0Size = csd0.size
            currentIndex = csd0Size.toLong()

//        mediaCodec = MediaCodec.createByCodecName("OMX.google.h265.decoder")
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
            mediaCodec.configure(format, surface, null, 0)
            outputFormat = mediaCodec.outputFormat // option B
            mediaCodec.setCallback(mediaCodecCallback)
        }.onFailure { it.printStackTrace();ToastUtil.showErrorToast("Init Decoder error") }
    }

    private val mediaCodecCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, inputBufferId: Int) {
            runCatching {
                val inputBuffer = codec.getInputBuffer(inputBufferId)
                // fill inputBuffer with valid data
                inputBuffer?.clear()
                val data = queue.poll()?.also {
//                LogContext.log.i(ITAG, "onInputBufferAvailable length=${it.size}")
                    inputBuffer?.put(it)
                    LogContext.log.w(TAG, "poll queue[${queue.size}] content_size=${it.size}")
                }
                codec.queueInputBuffer(inputBufferId, 0, data?.size ?: 0, computePresentationTimeUs(++frameCount), 0)
            }.onFailure {
                it.printStackTrace()
            }
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, outputBufferId: Int, info: MediaCodec.BufferInfo) {
            runCatching {
                val outputBuffer = codec.getOutputBuffer(outputBufferId)
                // val bufferFormat = codec.getOutputFormat(outputBufferId) // option A
                // bufferFormat is equivalent to member variable outputFormat
                // outputBuffer is ready to be processed or rendered.
                outputBuffer?.let {
                    when (info.flags) {
                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> Unit
                        MediaCodec.BUFFER_FLAG_KEY_FRAME -> LogContext.log.i(ITAG, "Found Key Frame[" + info.size + "]")
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM -> Unit
                        MediaCodec.BUFFER_FLAG_PARTIAL_FRAME -> Unit
                        else -> Unit
                    }
                }
                codec.releaseOutputBuffer(outputBufferId, true)
            }.onFailure { it.printStackTrace() }
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            LogContext.log.w(ITAG, "onOutputFormatChanged format=$format")
            // Subsequent data will conform to new format.
            // Can ignore if using getOutputFormat(outputBufferId)
            outputFormat = format // option B
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            LogContext.log.e(ITAG, "onError e=${e.message}")
        }
    }

    private fun computePresentationTimeUs(frameIndex: Long) = frameIndex * 1_000_000 / 24

    private lateinit var rf: RandomAccessFile

    private var currentIndex = 0L
    private fun getRawH265(): ByteArray? {
        val bufferSize = 100_000
        val bb = ByteArray(bufferSize)
        LogContext.log.e(TAG, "Current file pos=$currentIndex")
        rf.seek(currentIndex)
        var readSize = rf.read(bb, 0, bufferSize)
        if (readSize == -1) {
            return null
        }
        for (i in 4 until readSize) {
            if (findStartCode4(bb, readSize - i)) {
                readSize -= i
                break
            }
        }
        val wholeNalu = ByteArray(readSize)
        System.arraycopy(bb, 0, wholeNalu, 0, readSize)
        currentIndex += readSize
        return wholeNalu
    }

    private fun getNalu(): ByteArray? {
        var curIndex = 0
        val bb = ByteArray(100000)
        rf.read(bb, curIndex, 4)
        if (findStartCode4(bb, 0)) {
            curIndex = 4
        }
        var findNALStartCode = false
        var nextNalStartPos = 0
        var reWind = 0
        while (!findNALStartCode) {
            val hex = rf.read()
//            val naluType = getNaluType(hex.toByte())
//                LogContext.log.w(TAG, "NALU Type=$naluType")
            if (curIndex >= bb.size) {
                return null
            }
            bb[curIndex++] = hex.toByte()
            if (hex == -1) {
                nextNalStartPos = curIndex
            }
            if (findStartCode4(bb, curIndex - 4)) {
                findNALStartCode = true
                reWind = 4
                nextNalStartPos = curIndex - reWind
            }
        }
        val nal = ByteArray(nextNalStartPos)
        System.arraycopy(bb, 0, nal, 0, nextNalStartPos)
        val pos = rf.filePointer
        val setPos = pos - reWind
        rf.seek(setPos)
        return nal
    }

    // Find NALU prefix "00 00 00 01"
    private fun findStartCode4(bb: ByteArray, offSet: Int): Boolean {
        if (offSet < 0) {
            return false
        }
        return bb[offSet].toInt() == 0 && bb[offSet + 1].toInt() == 0 && bb[offSet + 2].toInt() == 0 && bb[offSet + 3].toInt() == 1
    }

    fun close() {
        queue.clear()
        ioScope.cancel()
        runCatching {
            LogContext.log.d(TAG, "close start")
            mediaCodec.stop()
            mediaCodec.release()
        }.onFailure { LogContext.log.e(TAG, "close error") }
    }

    fun startDecoding() {
        // FIXME
        // If use coroutines here, the video will be displayed. I don't know why!!!
        ioScope.launch {
            val startIdx = 4
            runCatching {
                while (true) {
                    val bytes = getRawH265() ?: break
                    var previousStart = 0
                    for (i in startIdx until bytes.size) {
                        ensureActive()
                        if (findStartCode4(bytes, i)) {
                            val frame = ByteArray(i - previousStart)
                            System.arraycopy(bytes, previousStart, frame, 0, frame.size)
                            queue.offer(frame)
                            LogContext.log.w(TAG, "offer queue[${queue.size}] content_size=${frame.size}") //  [${bytes.toHexStringLE()}]
                            previousStart = i
                            // FIXME We'd better control the FPS by SpeedManager
                            Thread.sleep(32)
                        }
                    }
                }
            }.onFailure { it.printStackTrace() }
        }
        mediaCodec.start()
    }

    @Suppress("unused")
    private fun getNaluType(nalu: Byte): Int = ((nalu.toInt() and 0x07E) shr 1)
}