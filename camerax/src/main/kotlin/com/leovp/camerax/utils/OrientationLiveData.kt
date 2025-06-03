/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.leovp.camerax.utils

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.view.OrientationEventListener
import android.view.Surface
import androidx.lifecycle.LiveData
import com.leovp.android.exts.isNormalLandscape
import com.leovp.android.exts.isNormalPortrait
import com.leovp.android.exts.isReverseLandscape
import com.leovp.android.exts.isReversePortrait

/**
 * Calculates closest 90-degree orientation to compensate for the device
 * rotation relative to sensor orientation, i.e., allows user to see camera
 * frames with the expected orientation.
 */
internal class OrientationLiveData(
    private val context: Context,
    characteristics: CameraCharacteristics,
) : LiveData<Int>() {

    private val listener = object : OrientationEventListener(context.applicationContext) {
        override fun onOrientationChanged(orientation: Int) {
            val deviceSurfaceRotation = when {
                context.isNormalPortrait(orientation) -> Surface.ROTATION_0
                context.isReverseLandscape(orientation) -> Surface.ROTATION_90
                context.isReversePortrait(orientation) -> Surface.ROTATION_180
                context.isNormalLandscape(orientation) -> Surface.ROTATION_270
                else -> return
            }
            val relative = computeRelativeRotation(characteristics, deviceSurfaceRotation)
            if (relative != value) postValue(relative)
        }
    }

    override fun onActive() {
        super.onActive()
        listener.enable()
    }

    override fun onInactive() {
        super.onInactive()
        listener.disable()
    }

    companion object {

        /**
         * Computes rotation required to transform from the camera sensor orientation to the
         * device's current orientation in degrees.
         *
         * @param characteristics the [CameraCharacteristics] to query for the sensor orientation.
         * @param deviceSurfaceRotation the current device orientation as a Surface constant
         * @return the relative rotation from the camera sensor to the current device orientation.
         */
        @JvmStatic
        private fun computeRelativeRotation(characteristics: CameraCharacteristics, deviceSurfaceRotation: Int): Int {
            val sensorOrientationDegrees = characteristics.cameraSensorOrientation()

            val deviceOrientationDegrees = when (deviceSurfaceRotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }

            // Reverse device orientation for front-facing cameras
            val sign = if (characteristics.get(CameraCharacteristics.LENS_FACING)
                == CameraCharacteristics.LENS_FACING_FRONT
            ) {
                1
            } else {
                -1
            }

            // Calculate desired JPEG orientation relative to camera orientation to make
            // the image upright relative to the device orientation
            return (sensorOrientationDegrees - (deviceOrientationDegrees * sign) + 360) % 360
        }
    }
}
