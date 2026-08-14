@file:Suppress("unused")

package com.leovp.camerax.utils

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList

/**
 * Author: Michael Leo
 * Date: 2022/4/26 13:23
 */

// MediaCodecList.ALL_CODECS
internal fun getCodecListByMimeType(
    mimeType: String,
    encoder: Boolean = true
): List<MediaCodecInfo> = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    .codecInfos
    .filter { it.isEncoder == encoder }
    .filter { it.supportedTypes.indexOfFirst { type -> type.equals(mimeType, true) } > -1 }

internal fun hasCodecByName(mimeType: String, codecName: String, encoder: Boolean = true): Boolean =
    getCodecListByMimeType(mimeType, encoder).indexOfFirst { it.name.equals(codecName, true) } > -1

internal fun getAllSupportedCodecList(): Array<MediaCodecInfo> =
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos // MediaCodecList.ALL_CODECS

/**
 * Runs [block] with a freshly created [MediaCodec] and always releases the codec afterwards.
 *
 * The capability helpers below only need the codec to read its [MediaCodecInfo.CodecCapabilities];
 * releasing it immediately avoids leaking a native encoder/decoder instance (previously these were
 * left to GC finalization). Capability arrays are plain Java-side copies, so they remain valid
 * after the codec is released.
 */
private inline fun <T> withCodecReleased(codec: MediaCodec, block: (MediaCodec) -> T): T =
    try {
        block(codec)
    } finally {
        codec.release()
    }

// Encoder capabilities are device-global and immutable at runtime. Cache them per mime so the
// throwaway MediaCodec is created only once per type instead of on every camera bind. Access is
// from the main thread only (camera parameter logging), so a plain HashMap is sufficient.
private val encoderColorFormatCache = HashMap<String, IntArray>()
private val encoderProfileLevelsCache = HashMap<String, Array<MediaCodecInfo.CodecProfileLevel>>()

/**
 * The result is the color format defined in MediaCodecInfo.CodecCapabilities.COLOR_Formatxxx
 */
internal fun getSupportedColorFormat(codec: MediaCodec, mime: String): IntArray =
    getSupportedColorFormat(codec.codecInfo.getCapabilitiesForType(mime))

internal fun getSupportedColorFormatForEncoder(mime: String): IntArray =
    encoderColorFormatCache.getOrPut(mime) {
        withCodecReleased(MediaCodec.createEncoderByType(mime)) { getSupportedColorFormat(it, mime) }
    }

internal fun getSupportedColorFormatForDecoder(mime: String): IntArray =
    withCodecReleased(MediaCodec.createDecoderByType(mime)) { getSupportedColorFormat(it, mime) }

private fun getSupportedColorFormat(caps: MediaCodecInfo.CodecCapabilities): IntArray =
    caps.colorFormats

internal fun getSupportedProfileLevels(
    codec: MediaCodec,
    mime: String
): Array<MediaCodecInfo.CodecProfileLevel> =
    getSupportedProfileLevels(codec.codecInfo.getCapabilitiesForType(mime))

internal fun getSupportedProfileLevelsForEncoder(
    mime: String
): Array<MediaCodecInfo.CodecProfileLevel> =
    encoderProfileLevelsCache.getOrPut(mime) {
        withCodecReleased(MediaCodec.createEncoderByType(mime)) { getSupportedProfileLevels(it, mime) }
    }

internal fun getSupportedProfileLevelsForDecoder(
    mime: String
): Array<MediaCodecInfo.CodecProfileLevel> =
    withCodecReleased(MediaCodec.createDecoderByType(mime)) { getSupportedProfileLevels(it, mime) }

private fun getSupportedProfileLevels(
    caps: MediaCodecInfo.CodecCapabilities
): Array<MediaCodecInfo.CodecProfileLevel> = caps.profileLevels

internal fun isSoftwareCodec(codecName: String): Boolean =
    codecName.startsWith("OMX.google.", ignoreCase = true) ||
        codecName.startsWith("c2.android.", ignoreCase = true) ||
        (
            !codecName.startsWith("OMX.", ignoreCase = true) &&
                !codecName.startsWith("c2.", ignoreCase = true)
            )
