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
            359 to Surface.ROTATION_0,
        )

        expectedRotations.forEach { (orientation, expectedRotation) ->
            assertEquals(
                expectedRotation,
                OrientationLiveData.orientationToSurfaceRotation(orientation),
                "Unexpected surface rotation for orientation=$orientation"
            )
        }
    }
}
