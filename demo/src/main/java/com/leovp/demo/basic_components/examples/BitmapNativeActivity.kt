package com.leovp.demo.basic_components.examples

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import androidx.annotation.DrawableRes
import androidx.lifecycle.lifecycleScope
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityBitmapNativeBinding
import com.leovp.jpeg_sdk.JPEGUtil
import com.leovp.lib_common_android.exts.createFile
import com.leovp.lib_common_kotlin.exts.round
import com.leovp.lib_image.*
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.system.measureNanoTime

class BitmapNativeActivity : BaseDemonstrationActivity<ActivityBitmapNativeBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityBitmapNativeBinding {
        return ActivityBitmapNativeBinding.inflate(layoutInflater)
    }

    override fun onResume() {
        super.onResume()

        val resId = R.drawable.img_3024x4032

        lifecycleScope.launch(Dispatchers.IO) {
            compressTest(resId)
            LogContext.log.w(ITAG, "==================================================")

            bitmapProcessByNative(resId)
            LogContext.log.w(ITAG, "==================================================")

            bitmapProcessByAndroid(resId)
            LogContext.log.w(ITAG, "==================================================")
        }
    }

    private fun bitmapProcessByAndroid(@Suppress("SameParameterValue") @DrawableRes resId: Int) {
        val beautyBmp = BitmapFactory.decodeResource(resources, resId)
        var androidProcessedBmp: Bitmap?
        var androidCost = measureNanoTime {
            androidProcessedBmp = beautyBmp.rotate(90f)
        }
        LogContext.log.w(ITAG, "android cw90 cost=${(androidCost / 1000f / 1000).round(3)}us")
        androidProcessedBmp?.writeToFile(createFile("JPEG",
            "11_android_cw90.jpg",
            Environment.DIRECTORY_PICTURES))

        androidCost = measureNanoTime {
            androidProcessedBmp = beautyBmp.rotate(180f)
        }
        LogContext.log.w(ITAG, "android 180 cost=${(androidCost / 1000f / 1000).round(3)}us")
        androidProcessedBmp?.writeToFile(createFile("JPEG",
            "12_android_180.jpg",
            Environment.DIRECTORY_PICTURES))

        androidCost = measureNanoTime {
            androidProcessedBmp = beautyBmp.rotate(270f)
        }
        LogContext.log.w(ITAG, "android ccw90 cost=${(androidCost / 1000f / 1000).round(3)}us")
        androidProcessedBmp?.writeToFile(createFile("JPEG",
            "13_android_ccw90.jpg",
            Environment.DIRECTORY_PICTURES))

        androidCost = measureNanoTime {
            androidProcessedBmp = beautyBmp.flipHorizontal()
        }
        LogContext.log.w(ITAG, "android horizontal cost=${(androidCost / 1000f / 1000).round(3)}us")
        androidProcessedBmp?.writeToFile(createFile("JPEG",
            "14_android_horizontal.jpg",
            Environment.DIRECTORY_PICTURES))

        androidCost = measureNanoTime {
            androidProcessedBmp = beautyBmp.flipVertical()
        }
        LogContext.log.w(ITAG, "android vertical cost=${(androidCost / 1000f / 1000).round(3)}ms")
        androidProcessedBmp?.writeToFile(createFile("JPEG",
            "15_android_vertical.jpg",
            Environment.DIRECTORY_PICTURES))
    }

    private fun bitmapProcessByNative(@Suppress("SameParameterValue") @DrawableRes resId: Int) {
        var bmp = BitmapFactory.decodeResource(resources, resId)

        var bmpFromNative: Bitmap?
        var cost = measureNanoTime {
            bmpFromNative = BitmapProcessor(bmp).run {
                rotateBitmapCw90()
                getBitmapAndFree()
            }
        }
        LogContext.log.w(ITAG, "native cw90 cost=${(cost / 1000f / 1000).round(3)}ms")
        bmpFromNative?.writeToFile(createFile("JPEG",
            "01_native_cw90.jpg",
            Environment.DIRECTORY_PICTURES))

        cost = measureNanoTime {
            bmpFromNative = BitmapProcessor(bmp).run {
                rotateBitmap180()
                getBitmapAndFree()
            }
        }
        LogContext.log.w(ITAG, "native 180 cost=${(cost / 1000f / 1000).round(3)}ms")
        bmpFromNative?.writeToFile(createFile("JPEG",
            "02_native_180.jpg",
            Environment.DIRECTORY_PICTURES))

        cost = measureNanoTime {
            bmpFromNative = BitmapProcessor(bmp).run {
                rotateBitmapCcw90()
                getBitmapAndFree()
            }
        }
        LogContext.log.w(ITAG, "native ccw90 cost=${(cost / 1000f / 1000).round(3)}ms")
        bmpFromNative?.writeToFile(createFile("JPEG",
            "03_native_ccw90.jpg",
            Environment.DIRECTORY_PICTURES))

        cost = measureNanoTime {
            bmpFromNative = BitmapProcessor(bmp).run {
                flipBitmapHorizontal()
                getBitmapAndFree()
            }
        }
        LogContext.log.w(ITAG, "native horizontal cost=${(cost / 1000f / 1000).round(3)}ms")
        bmpFromNative?.writeToFile(createFile("JPEG",
            "04_native_horizontal.jpg",
            Environment.DIRECTORY_PICTURES))

        cost = measureNanoTime {
            bmpFromNative = BitmapProcessor(bmp).run {
                flipBitmapVertical()
                getBitmapAndFree()
            }
        }
        LogContext.log.w(ITAG, "native flip vertical cost=${(cost / 1000f / 1000).round(3)}ms")
        bmpFromNative?.writeToFile(createFile("JPEG",
            "05_native_vertical.jpg",
            Environment.DIRECTORY_PICTURES))

        bmp = BitmapFactory.decodeResource(resources, resId)
        cost = measureNanoTime {
            bmpFromNative = BitmapProcessor(bmp).run {
                cropBitmap(150, 150, 350, 350)
                getBitmapAndFree()
            }
        }
        LogContext.log.w(ITAG, "native crop cost=${(cost / 1000f / 1000).round(3)}ms")
        bmpFromNative?.writeToFile(createFile("JPEG",
            "06_native_crop.jpg",
            Environment.DIRECTORY_PICTURES))

        bmp = BitmapFactory.decodeResource(resources, resId)
        cost = measureNanoTime {
            bmpFromNative = BitmapProcessor(bmp).run {
                scaleBitmap(200, 200, BitmapProcessor.ScaleMethod.NearestNeighbour)
                getBitmapAndFree()
            }
        }
        LogContext.log.w(ITAG, "native scale cost=${(cost / 1000f / 1000).round(3)}ms")
        bmpFromNative?.writeToFile(createFile("JPEG",
            "07_native_scale.jpg",
            Environment.DIRECTORY_PICTURES))
    }

    private fun compressTest(@Suppress("SameParameterValue") @DrawableRes resId: Int) {
        // Attention:
        // If you put image in dimension related drawable folder, like [drawable], [drawable-xxhdpi],
        // [BitmapFactory.decodeResource] will take into account the screen dimension.
        //
        // So if your want to get the real size of image, put your images in [drawable-nodpi] folder.
        //
        // https://developer.android.com/training/multiscreen/screendensities#TaskProvideAltBmp
        // https://developer.android.com/guide/topics/resources/providing-resources
        var bmp = BitmapFactory.decodeResource(resources, resId)
        var outFile = createFile("JPEG", "compress_by_native.jpg", Environment.DIRECTORY_PICTURES)
        var cost = measureNanoTime {
            JPEGUtil.compressBitmap(bmp, 90, outFile.absolutePath, true)
        }
        LogContext.log.w(ITAG, "Compress by native cost=${(cost / 1000f / 1000).round(3)}ms")

        LogContext.log.w(ITAG, "==================================================")

        bmp = BitmapFactory.decodeResource(resources, resId)
        outFile = createFile("JPEG", "compress_by_android.jpg", Environment.DIRECTORY_PICTURES)
        cost = measureNanoTime {
            bmp.writeToFile(outFile, 90)
        }
        LogContext.log.w(ITAG, "Compress by android cost=${(cost / 1000f / 1000).round(3)}ms")
    }
}