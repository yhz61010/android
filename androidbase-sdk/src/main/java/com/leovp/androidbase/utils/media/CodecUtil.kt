package com.leovp.androidbase.utils.media

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
    fun getCodecListByMimeType(mimeType: String, encoder: Boolean = true): List<MediaCodecInfo> = MediaCodecList(MediaCodecList.REGULAR_CODECS) // MediaCodecList.ALL_CODECS
        .codecInfos.filter { it.isEncoder == encoder }.filter { it.supportedTypes.indexOfFirst { type -> type.equals(mimeType, true) } > -1 }

    @Suppress("unused")
    fun hasCodecByName(mimeType: String, codecName: String, encoder: Boolean = true): Boolean =
        getCodecListByMimeType(mimeType, encoder).indexOfFirst { it.name.equals(codecName, true) } > -1

    @Suppress("unused")
    fun getAllSupportedCodecList(): Array<MediaCodecInfo> = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos // MediaCodecList.ALL_CODECS

    /**
     * The result is the color format defined in MediaCodecInfo.CodecCapabilities.COLOR_Formatxxx
     */
    fun getSupportedColorFormat(codec: MediaCodec, mime: String): IntArray = getSupportedColorFormat(codec.codecInfo.getCapabilitiesForType(mime))
    fun getSupportedColorFormat(caps: CodecCapabilities): IntArray = caps.colorFormats
    fun getSupportedColorFormatForEncoder(mime: String): IntArray = getSupportedColorFormat(MediaCodec.createEncoderByType(mime), mime)
    fun getSupportedColorFormatForDecoder(mime: String): IntArray = getSupportedColorFormat(MediaCodec.createDecoderByType(mime), mime)

    fun getSupportedProfileLevels(codec: MediaCodec, mime: String): Array<MediaCodecInfo.CodecProfileLevel> =
        getSupportedProfileLevels(codec.codecInfo.getCapabilitiesForType(mime))

    fun getSupportedProfileLevels(caps: CodecCapabilities): Array<MediaCodecInfo.CodecProfileLevel> = caps.profileLevels
    fun getSupportedProfileLevelsForEncoder(mime: String): Array<MediaCodecInfo.CodecProfileLevel> = getSupportedProfileLevels(MediaCodec.createEncoderByType(mime), mime)
    fun getSupportedProfileLevelsForDecoder(mime: String): Array<MediaCodecInfo.CodecProfileLevel> = getSupportedProfileLevels(MediaCodec.createDecoderByType(mime), mime)
}