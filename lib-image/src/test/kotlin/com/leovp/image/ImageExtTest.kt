package com.leovp.image

import android.graphics.Bitmap
import java.nio.ByteBuffer
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImageExtTest {
    @Test
    fun `bitmap bytes preserve pixels`() {
        val source = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            setPixels(
                intArrayOf(
                    0xFF112233.toInt(),
                    0xFF445566.toInt(),
                    0xFF778899.toInt(),
                    0xFFAABBCC.toInt()
                ),
                0,
                2,
                0,
                0,
                2,
                2
            )
        }

        val bytes = source.toBytes()
        val restored = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        }

        assertEquals(source.byteCount, bytes.size)
        assertContentEquals(
            IntArray(4).also { source.getPixels(it, 0, 2, 0, 0, 2, 2) },
            IntArray(4).also { restored.getPixels(it, 0, 2, 0, 0, 2, 2) }
        )

        source.recycle()
        restored.recycle()
    }
}
