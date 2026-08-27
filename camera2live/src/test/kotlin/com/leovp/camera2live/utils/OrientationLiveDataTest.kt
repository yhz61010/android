package com.leovp.camera2live.utils

import android.view.OrientationEventListener
import android.view.Surface
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class OrientationLiveDataTest {
    @Test
    fun `unknown orientation has no surface rotation`() {
        assertNull(
            OrientationLiveData.orientationToSurfaceRotation(
                OrientationEventListener.ORIENTATION_UNKNOWN
            )
        )
    }

    @Test
    fun `orientation boundaries map to the nearest surface rotation`() {
        val expectedRotations = mapOf(
            0 to Surface.ROTATION_0,
            45 to Surface.ROTATION_0,
            46 to Surface.ROTATION_90,
            135 to Surface.ROTATION_90,
            136 to Surface.ROTATION_180,
            225 to Surface.ROTATION_180,
            226 to Surface.ROTATION_270,
            315 to Surface.ROTATION_270,
            316 to Surface.ROTATION_0,
            359 to Surface.ROTATION_0
        )

        expectedRotations.forEach { (orientation, expectedRotation) ->
            assertEquals(
                expectedRotation,
                OrientationLiveData.orientationToSurfaceRotation(orientation),
                "Unexpected surface rotation for orientation=$orientation"
            )
        }
    }

    @Test
    fun `relative rotation covers sensor orientation lens facing and device rotation`() {
        data class Case(
            val sensorOrientationDegrees: Int,
            val lensFacingFront: Boolean,
            val surfaceRotation: Int,
            val expected: Int,
        )

        val cases = listOf(
            // sensor 90, front (sign = +1): (90 - deviceDeg + 360) % 360
            Case(90, true, Surface.ROTATION_0, 90),
            Case(90, true, Surface.ROTATION_90, 0),
            Case(90, true, Surface.ROTATION_180, 270),
            Case(90, true, Surface.ROTATION_270, 180),
            // sensor 90, back (sign = -1): (90 + deviceDeg + 360) % 360
            Case(90, false, Surface.ROTATION_0, 90),
            Case(90, false, Surface.ROTATION_90, 180),
            Case(90, false, Surface.ROTATION_180, 270),
            Case(90, false, Surface.ROTATION_270, 0),
            // sensor 270, front (sign = +1): (270 - deviceDeg + 360) % 360
            Case(270, true, Surface.ROTATION_0, 270),
            Case(270, true, Surface.ROTATION_90, 180),
            Case(270, true, Surface.ROTATION_180, 90),
            Case(270, true, Surface.ROTATION_270, 0),
            // sensor 270, back (sign = -1): (270 + deviceDeg + 360) % 360
            Case(270, false, Surface.ROTATION_0, 270),
            Case(270, false, Surface.ROTATION_90, 0),
            Case(270, false, Surface.ROTATION_180, 90),
            Case(270, false, Surface.ROTATION_270, 180)
        )

        cases.forEach { case ->
            assertEquals(
                case.expected,
                OrientationLiveData.computeRelativeRotation(
                    case.sensorOrientationDegrees,
                    case.lensFacingFront,
                    case.surfaceRotation
                ),
                "sensor=${case.sensorOrientationDegrees} " +
                    "front=${case.lensFacingFront} rotation=${case.surfaceRotation}"
            )
        }
    }
}
