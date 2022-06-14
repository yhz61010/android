@file:Suppress("unused")

package com.leovp.camerax_sdk.utils

import androidx.camera.core.AspectRatio
import androidx.camera.video.Quality

/**
 * a helper function to retrieve the aspect ratio from a QualitySelector enum.
 */
fun Quality.getAspectRatio(): Int {
    return when {
        arrayOf(Quality.UHD, Quality.FHD, Quality.HD).contains(this) -> AspectRatio.RATIO_16_9
        (this == Quality.SD)                                         -> AspectRatio.RATIO_4_3
        else                                                         -> throw UnsupportedOperationException()
    }
}

/**
 * a helper function to retrieve the aspect ratio string from a Quality enum.
 */
fun Quality.getAspectRatioString(portraitMode: Boolean): String {
    val hdQualities = arrayOf(Quality.UHD, Quality.FHD, Quality.HD)
    val ratio = when {
        hdQualities.contains(this) -> Pair(16, 9)
        this == Quality.SD         -> Pair(4, 3)
        else                       -> throw UnsupportedOperationException()
    }

    return if (portraitMode) "V,${ratio.second}:${ratio.first}"
    else "H,${ratio.first}:${ratio.second}"
}

/**
 * Get the name (a string) from the given Video.Quality object.
 */
fun Quality.getNameString(): String {
    return when (this) {
        Quality.UHD -> "QUALITY_UHD(2160p)"
        Quality.FHD -> "QUALITY_FHD(1080p)"
        Quality.HD  -> "QUALITY_HD(720p)"
        Quality.SD  -> "QUALITY_SD(480p)"
        else        -> throw IllegalArgumentException("Quality $this is NOT supported")
    }
}

/**
 * Translate Video.Quality name(a string) to its Quality object.
 */
fun Quality.getQualityObject(name: String): Quality {
    return when (name) {
        Quality.UHD.getNameString() -> Quality.UHD
        Quality.FHD.getNameString() -> Quality.FHD
        Quality.HD.getNameString()  -> Quality.HD
        Quality.SD.getNameString()  -> Quality.SD
        else                        -> throw IllegalArgumentException("Quality string $name is NOT supported")
    }
}
