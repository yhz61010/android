package com.leovp.jpeg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JPEGUtilInstrumentedTest {
    @Test
    fun compressBitmapWritesDecodableJpeg() {
        val output =
            File(
                ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
                "native-jpeg-test.jpg"
            )
        output.delete()
        val bitmap = Bitmap.createBitmap(
            intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.BLACK, Color.YELLOW),
            3,
            2,
            Bitmap.Config.ARGB_8888
        )

        assertEquals(0, JPEGUtil.compressBitmap(bitmap, 90, output.absolutePath, true))
        assertTrue(output.length() > 2)
        output.inputStream().use { input ->
            assertEquals(0xff, input.read())
            assertEquals(0xd8, input.read())
        }
        val decoded = BitmapFactory.decodeFile(output.absolutePath)
        assertEquals(3, decoded.width)
        assertEquals(2, decoded.height)
        output.delete()
    }

    @Test
    fun invalidArgumentsFailWithoutCreatingOutput() {
        val output =
            File(
                ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
                "invalid-native-jpeg.jpg"
            )
        output.delete()
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        assertThrows(IllegalArgumentException::class.java) {
            JPEGUtil.compressBitmap(bitmap, 101, output.absolutePath, false)
        }
        assertFalse(output.exists())
        assertThrows(IllegalArgumentException::class.java) {
            JPEGUtil.compressBitmap(bitmap, 80, "", false)
        }
        val rgb565 = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
        assertThrows(IllegalArgumentException::class.java) {
            JPEGUtil.compressBitmap(rgb565, 80, output.absolutePath, false)
        }
        assertFalse(output.exists())
    }
}
