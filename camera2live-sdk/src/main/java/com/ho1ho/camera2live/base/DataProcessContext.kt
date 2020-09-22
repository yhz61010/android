package com.ho1ho.camera2live.base

import android.media.Image
import com.ho1ho.camera2live.base.iters.IDataProcessStrategy

/**
 * Author: Michael Leo
 * Date: 20-4-1 上午11:06
 */
class DataProcessContext(private var strategy: IDataProcessStrategy) {
    fun doProcess(image: Image, lensFacing: Int, cameraSensorOrientation: Int): ByteArray {
        return strategy.doProcess(image, lensFacing, cameraSensorOrientation)
    }
}