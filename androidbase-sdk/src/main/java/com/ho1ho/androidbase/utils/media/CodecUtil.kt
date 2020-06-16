package com.ho1ho.androidbase.utils.media

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
}