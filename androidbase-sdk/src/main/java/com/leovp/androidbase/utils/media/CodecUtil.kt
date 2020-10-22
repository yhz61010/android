package com.leovp.androidbase.utils.media

import android.graphics.ImageFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecList

/**
 * Author: Michael Leo
 * Date: 20-5-20 上午9:48
 */
object CodecUtil {
    @Suppress("unused")
    fun getEncoderListByMimeType(mimeType: String) = MediaCodecList(MediaCodecList.ALL_CODECS)
        .codecInfos.filter { it.isEncoder }
        .filter { it.supportedTypes.indexOfFirst { type -> type.equals(mimeType, true) } > -1 }

    @Suppress("unused")
    fun hasEncoderByCodecName(mimeType: String, codecName: String) =
        getEncoderListByMimeType(mimeType)
            .indexOfFirst { it.name.equals(codecName, true) } > -1

    @Suppress("unused")
    fun getAllSupportedCodecList(): Array<MediaCodecInfo> = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos

    /**
     * The result is the color format defined in MediaCodecInfo.CodecCapabilities.COLOR_Formatxxx
     */
    fun getSupportedColorFormat(codec: MediaCodec, mime: String): IntArray = getSupportedColorFormat(codec.codecInfo.getCapabilitiesForType(mime))
    fun getSupportedColorFormat(caps: CodecCapabilities): IntArray = caps.colorFormats
    fun getSupportedColorFormatForEncoder(mime: String): IntArray = getSupportedColorFormat(MediaCodec.createEncoderByType(mime), mime)
    fun getSupportedColorFormatForDecoder(mime: String): IntArray = getSupportedColorFormat(MediaCodec.createDecoderByType(mime), mime)

    fun getSupportedProfileLevels(codec: MediaCodec, mime: String) = getSupportedProfileLevels(codec.codecInfo.getCapabilitiesForType(mime))
    fun getSupportedProfileLevels(caps: CodecCapabilities) = caps.profileLevels
    fun getSupportedProfileLevelsForEncoder(mime: String) = getSupportedProfileLevels(MediaCodec.createEncoderByType(mime), mime)
    fun getSupportedProfileLevelsForDecoder(mime: String) = getSupportedProfileLevels(MediaCodec.createDecoderByType(mime), mime)

    @Suppress("unused")
    fun getImageFormatName(imageFormat: Int) = when (imageFormat) {
        ImageFormat.JPEG -> "JPEG"
        ImageFormat.YUV_420_888 -> "YUV_420_888"
        ImageFormat.YUV_422_888 -> "YUV_422_888"
        ImageFormat.YUV_444_888 -> "YUV_444_888"
        ImageFormat.NV16 -> "NV16"
        ImageFormat.NV21 -> "NV21"
        ImageFormat.HEIC -> "HEIC"
        ImageFormat.RGB_565 -> "RGB_565"
        ImageFormat.YUY2 -> "YUY2"
        ImageFormat.YV12 -> "YV12"
        else -> imageFormat.toString()
    }
}