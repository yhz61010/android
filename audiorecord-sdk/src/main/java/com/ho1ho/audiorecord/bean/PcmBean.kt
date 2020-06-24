package com.ho1ho.audiorecord.bean

import androidx.annotation.Keep

/**
 * Author: Michael Leo
 * Date: 20-2-26 上午11:27
 *
 * @param pcm ENCODING_PCM_16BIT pcm data array
 */
@Keep
data class PcmBean(val pcm: ShortArray, val readSize: Int)