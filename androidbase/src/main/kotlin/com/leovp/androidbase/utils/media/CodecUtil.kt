@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.leovp.androidbase.utils.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecList
import android.os.Build
import com.leovp.androidbase.exts.kotlin.TreeElement
import com.leovp.androidbase.exts.kotlin.printTree
import com.leovp.log.LogContext
import java.util.Locale

/**
 * Author: Michael Leo
 * Date: 20-5-20 上午9:48
 */
object CodecUtil {
    private const val TAG = "CodecUtil"

    // MediaCodecList.ALL_CODECS
    fun getCodecListByMimeType(mimeType: String, encoder: Boolean = true): List<MediaCodecInfo> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos.filter { it.isEncoder == encoder }
            .filter { it.supportedTypes.indexOfFirst { type -> type.equals(mimeType, true) } > -1 }

    fun hasCodecByName(mimeType: String, codecName: String, encoder: Boolean = true): Boolean =
        getCodecListByMimeType(mimeType, encoder).indexOfFirst { it.name.equals(codecName, true) } > -1

    fun getAllSupportedCodecList(): Array<MediaCodecInfo> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos // MediaCodecList.ALL_CODECS

    /**
     * The result is the color format defined in MediaCodecInfo.CodecCapabilities.COLOR_Formatxxx
     */
    fun getSupportedColorFormat(codec: MediaCodec, mime: String): IntArray =
        getSupportedColorFormat(codec.codecInfo.getCapabilitiesForType(mime))

    fun getSupportedColorFormatForEncoder(mime: String): IntArray =
        getSupportedColorFormat(MediaCodec.createEncoderByType(mime), mime)

    fun getSupportedColorFormatForDecoder(mime: String): IntArray =
        getSupportedColorFormat(MediaCodec.createDecoderByType(mime), mime)

    private fun getSupportedColorFormat(caps: CodecCapabilities): IntArray = caps.colorFormats

    fun getSupportedProfileLevels(codec: MediaCodec, mime: String): Array<MediaCodecInfo.CodecProfileLevel> =
        getSupportedProfileLevels(codec.codecInfo.getCapabilitiesForType(mime))

    fun getSupportedProfileLevelsForEncoder(mime: String): Array<MediaCodecInfo.CodecProfileLevel> =
        getSupportedProfileLevels(MediaCodec.createEncoderByType(mime), mime)

    fun getSupportedProfileLevelsForDecoder(mime: String): Array<MediaCodecInfo.CodecProfileLevel> =
        getSupportedProfileLevels(MediaCodec.createDecoderByType(mime), mime)

    private fun getSupportedProfileLevels(caps: CodecCapabilities): Array<MediaCodecInfo.CodecProfileLevel> =
        caps.profileLevels

    fun isSoftwareCodec(codecName: String): Boolean = codecName.startsWith("OMX.google.", ignoreCase = true) ||
        codecName.startsWith("c2.android.", ignoreCase = true) ||
        (
            !codecName.startsWith("OMX.", ignoreCase = true) &&
                !codecName.startsWith("c2.", ignoreCase = true)
            )

    fun printMediaCodecsList() {
        val mediaCodecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val mediaCodecInfos = mediaCodecList.codecInfos
        val lv1List = ArrayList<TreeElement>()
        for (mediaCodecInfo in mediaCodecInfos) {
            // val isEncoder = mediaCodecInfo.isEncoder
            // val isDecoder = !isEncoder
            val str = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                String.format(
                    Locale.getDefault(),
                    "name: %s, encoder: %b hardware: %b, software: %b, vendor: %b",
                    mediaCodecInfo.name,
                    mediaCodecInfo.isEncoder,
                    mediaCodecInfo.isHardwareAccelerated,
                    mediaCodecInfo.isSoftwareOnly,
                    mediaCodecInfo.isVendor
                )
                // LogContext.log.i(ITAG, str)
            } else {
                "Below Android Q"
            }
            val lv1Element = TreeElement(str, null)
            lv1List.add(lv1Element)

            val types = mediaCodecInfo.supportedTypes
            val lv2List = ArrayList<TreeElement>()
            lv1Element.children = lv2List
            for (type in types) {
                // LogContext.log.i(ITAG, "type: $type")
                val lv2Element = TreeElement("type: $type", null)
                lv2List.add(lv2Element)
                val capabilities = mediaCodecInfo.getCapabilitiesForType(type)

                val lv3List = ArrayList<TreeElement>()
                lv2Element.children = lv3List
                for (colorFormat in capabilities.colorFormats) {
                    var colorFormatName = "unknown"
                    @Suppress("DEPRECATION")
                    when (colorFormat) {
                        CodecCapabilities.COLOR_FormatYUV420Flexible -> {
                            colorFormatName = "YUV420Flexible"
                        }

                        CodecCapabilities.COLOR_FormatSurface -> {
                            colorFormatName = "FormatSurface"
                        }

                        CodecCapabilities.COLOR_FormatYUV420SemiPlanar -> {
                            colorFormatName = "YUV420SemiPlanar"
                        }

                        CodecCapabilities.COLOR_FormatYUV420PackedPlanar -> {
                            colorFormatName = "YUV420PackedPlanar"
                        }

                        CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar -> {
                            colorFormatName = "YUV420PackedSemiPlanar"
                        }

                        CodecCapabilities.COLOR_FormatYUV420Planar -> {
                            colorFormatName = "YUV420Planar"
                        }
                    }
                    val strText = String.format(
                        Locale.getDefault(),
                        "colorFormat: %s (%s)", colorFormat.toString().padStart(10, ' '),
                        colorFormatName
                    )
                    // LogContext.log.i(ITAG, str)
                    val lv3Element = TreeElement(strText, null)
                    lv3List.add(lv3Element)
                }
            }
        } // root for

        val root = TreeElement("MediaCodec", lv1List)
        printTree(root, "", true) { LogContext.log.i(TAG, it) }
    }

    // ==========

    // Find NALU prefix "00 00 00 01"
    fun findStartCode(data: ByteArray, offSet: Int = 0): Boolean {
        if (offSet < 0 || data.size < 4) return false
        return data[offSet].toInt() == 0 &&
            data[offSet + 1].toInt() == 0 &&
            data[offSet + 2].toInt() == 0 &&
            data[offSet + 3].toInt() == 1
    }
}
