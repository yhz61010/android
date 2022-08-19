@file:Suppress("unused")

package com.leovp.camerax_sdk.utils

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList

/**
 * Author: Michael Leo
 * Date: 2022/4/26 13:23
 */

// MediaCodecList.ALL_CODECS
internal fun getCodecListByMimeType(mimeType: String, encoder: Boolean = true): List<MediaCodecInfo> = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    .codecInfos.filter { it.isEncoder == encoder }.filter { it.supportedTypes.indexOfFirst { type -> type.equals(mimeType, true) } > -1 }

internal fun hasCodecByName(mimeType: String, codecName: String, encoder: Boolean = true): Boolean =
    getCodecListByMimeType(mimeType, encoder).indexOfFirst { it.name.equals(codecName, true) } > -1

internal fun getAllSupportedCodecList(): Array<MediaCodecInfo> = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos // MediaCodecList.ALL_CODECS

/**
 * The result is the color format defined in MediaCodecInfo.CodecCapabilities.COLOR_Formatxxx
 */
internal fun getSupportedColorFormat(codec: MediaCodec, mime: String): IntArray =
    getSupportedColorFormat(codec.codecInfo.getCapabilitiesForType(mime))
internal fun getSupportedColorFormatForEncoder(mime: String): IntArray = getSupportedColorFormat(MediaCodec.createEncoderByType(mime), mime)
internal fun getSupportedColorFormatForDecoder(mime: String): IntArray = getSupportedColorFormat(MediaCodec.createDecoderByType(mime), mime)
private fun getSupportedColorFormat(caps: MediaCodecInfo.CodecCapabilities): IntArray = caps.colorFormats

internal fun getSupportedProfileLevels(codec: MediaCodec, mime: String): Array<MediaCodecInfo.CodecProfileLevel> =
    getSupportedProfileLevels(codec.codecInfo.getCapabilitiesForType(mime))

internal fun getSupportedProfileLevelsForEncoder(mime: String): Array<MediaCodecInfo.CodecProfileLevel> =
    getSupportedProfileLevels(MediaCodec.createEncoderByType(mime), mime)
internal fun getSupportedProfileLevelsForDecoder(mime: String): Array<MediaCodecInfo.CodecProfileLevel> =
    getSupportedProfileLevels(MediaCodec.createDecoderByType(mime), mime)
private fun getSupportedProfileLevels(caps: MediaCodecInfo.CodecCapabilities): Array<MediaCodecInfo.CodecProfileLevel> = caps.profileLevels

internal fun isSoftwareCodec(codecName: String): Boolean {
    return codecName.startsWith("OMX.google.", ignoreCase = true) || codecName.startsWith("c2.android.", ignoreCase = true) ||
        (!codecName.startsWith("OMX.", ignoreCase = true) && !codecName.startsWith("c2.", ignoreCase = true))
}
