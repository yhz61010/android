package com.ho1ho.camera2live.base.iters

import android.media.Image

/**
 * Author: Michael Leo
 * Date: 20-4-1 上午11:05
 */
interface IDataProcessStrategy {
    fun doProcess(image: Image, lensFacing: Int, cameraSensorOrientation: Int): ByteArray
}