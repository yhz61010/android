package com.ho1ho.camera2live.base.iters

import android.media.Image
import androidx.annotation.IntDef

/**
 * Author: Michael Leo
 * Date: 20-4-1 上午11:05
 */
interface IDataProcessStrategy {
    fun doProcess(image: Image, lensFacing: Int, @CameraSensorOrientation cameraSensorOrientation: Int): ByteArray
}

@IntDef(flag = true, value = [0, 90, 180, 270])
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
annotation class CameraSensorOrientation