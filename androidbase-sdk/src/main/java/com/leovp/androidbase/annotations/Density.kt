package com.leovp.androidbase.annotations

import androidx.annotation.IntDef

/**
 * Author: Michael Leo
 * Date: 21-1-26 下午3:19
 */
@IntDef(Density.WIDTH_BASED, Density.HEIGHT_BASED, Density.LONG_SIDE_BASED, Density.SHORT_SIDE_BASED)
@kotlin.annotation.Retention(AnnotationRetention.SOURCE)
annotation class Density {
    companion object {
        const val WIDTH_BASED = 1
        const val HEIGHT_BASED = 2
        const val LONG_SIDE_BASED = 3
        const val SHORT_SIDE_BASED = 4
    }
}
