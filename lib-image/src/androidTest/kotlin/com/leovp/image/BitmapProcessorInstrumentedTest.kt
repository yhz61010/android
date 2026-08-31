package com.leovp.image

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapProcessorInstrumentedTest {
    @Test
    fun rotateAndFlipPreserveExpectedPixels() {
        val source = Bitmap.createBitmap(
            intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE),
            2,
            2,
            Bitmap.Config.ARGB_8888
        )

        val rotated = BitmapProcessor(source).use { processor ->
            processor.rotateBitmapCw90()
            processor.bitmap
        }
        assertArrayEquals(
            intArrayOf(Color.BLUE, Color.RED, Color.WHITE, Color.GREEN),
            rotated.readPixels()
        )

        val flipped = BitmapProcessor(source).use { processor ->
            processor.flipBitmapHorizontal()
            processor.bitmap
        }
        assertArrayEquals(
            intArrayOf(Color.GREEN, Color.RED, Color.WHITE, Color.BLUE),
            flipped.readPixels()
        )
    }

    @Test
    fun cropAndScaleValidateBoundsAndOnePixelDimensions() {
        val source = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        BitmapProcessor(source).use { processor ->
            assertThrows(IllegalArgumentException::class.java) {
                processor.cropBitmap(-1, 0, 1, 1)
            }
            processor.scaleBitmap(1, 3, BitmapProcessor.ScaleMethod.BilinearInterpolation)
            assertEquals(1, processor.bitmap.width)
            assertEquals(3, processor.bitmap.height)
            assertThrows(IllegalArgumentException::class.java) {
                processor.scaleBitmap(0, 1)
            }
        }
    }

    @Test
    fun bilinearScaleSupportsSinglePixelSourceAxes() {
        val oneByThree = Bitmap.createBitmap(
            intArrayOf(Color.RED, Color.GREEN, Color.BLUE),
            1,
            3,
            Bitmap.Config.ARGB_8888
        )
        BitmapProcessor(oneByThree).use { processor ->
            processor.scaleBitmap(4, 2, BitmapProcessor.ScaleMethod.BilinearInterpolation)
            val result = processor.bitmap
            assertEquals(4, result.width)
            assertEquals(2, result.height)
        }

        val threeByOne = Bitmap.createBitmap(
            intArrayOf(Color.RED, Color.GREEN, Color.BLUE),
            3,
            1,
            Bitmap.Config.ARGB_8888
        )
        BitmapProcessor(threeByOne).use { processor ->
            processor.scaleBitmap(2, 4, BitmapProcessor.ScaleMethod.BilinearInterpolation)
            val result = processor.bitmap
            assertEquals(2, result.width)
            assertEquals(4, result.height)
        }
    }

    @Test
    fun closeIsIdempotentAndUseAfterCloseFails() {
        val processor = BitmapProcessor(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        processor.close()
        processor.close()

        assertThrows(IllegalStateException::class.java) { processor.bitmap }
    }

    private fun Bitmap.readPixels(): IntArray =
        IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
}
