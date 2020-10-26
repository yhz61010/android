package com.leovp.androidbase.utils.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.media.Image

/**
 * Author: Michael Leo
 * Date: 20-3-25 上午11:20
 */
object YuvUtil {
    const val COLOR_FORMAT_I420 = 1

    @Suppress("unused")
    const val COLOR_FORMAT_NV21 = 2

    private fun isImageFormatSupported(image: Image): Boolean {
        when (image.format) {
            ImageFormat.YUV_420_888, ImageFormat.NV21, ImageFormat.YV12 -> return true
        }
        return false
    }

    @Suppress("unused")
    fun cropYUV420(data: ByteArray, imageW: Int, imageH: Int, newImageH: Int): ByteArray {
        var i: Int
        var j: Int
        val tmp: Int
        val yuv = ByteArray(imageW * newImageH * 3 / 2)
        val cropH: Int = (imageH - newImageH) / 2
        var count = 0
        j = cropH
        while (j < cropH + newImageH) {
            i = 0
            while (i < imageW) {
                yuv[count++] = data[j * imageW + i]
                i++
            }
            j++
        }

        //Cr Cb
        tmp = imageH + cropH / 2
        j = tmp
        while (j < tmp + newImageH / 2) {
            i = 0
            while (i < imageW) {
                yuv[count++] = data[j * imageW + i]
                i++
            }
            j++
        }
        return yuv
    }

    // byte[] data = getYuvDataFromImage(image, COLOR_FormatI420);
    // Return data in YYYYYYYYUUVV(I420/YU12)
    fun getYuvDataFromImage(image: Image, colorFormat: Int): ByteArray {
        require(!(colorFormat != COLOR_FORMAT_I420 && colorFormat != COLOR_FORMAT_NV21)) { "Only support COLOR_FormatI420 and COLOR_FormatNV21" }
        if (!isImageFormatSupported(image)) {
            throw RuntimeException("Can't convert Image to byte array, format " + image.format)
        }
        val crop = image.cropRect
        val format = image.format
        val width = crop.width()
        val height = crop.height()
        val planes = image.planes
        val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
        val rowData = ByteArray(planes[0].rowStride)
        var channelOffset = 0
        var outputStride = 1
        for (i in planes.indices) {
            when (i) {
                0 -> {
                    channelOffset = 0
                    outputStride = 1
                }
                1 -> {
                    if (colorFormat == COLOR_FORMAT_I420) {
                        channelOffset = width * height
                        outputStride = 1
                    } else if (colorFormat == COLOR_FORMAT_NV21) {
                        channelOffset = width * height + 1
                        outputStride = 2
                    }
                }
                2 -> {
                    if (colorFormat == COLOR_FORMAT_I420) {
                        channelOffset = (width * height * 1.25).toInt()
                        outputStride = 1
                    } else if (colorFormat == COLOR_FORMAT_NV21) {
                        channelOffset = width * height
                        outputStride = 2
                    }
                }
            }
            val buffer = planes[i].buffer
            val rowStride = planes[i].rowStride
            val pixelStride = planes[i].pixelStride
            val shift = if (i == 0) 0 else 1
            val w = width shr shift
            val h = height shr shift
            buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
            for (row in 0 until h) {
                var length: Int
                if (pixelStride == 1 && outputStride == 1) {
                    length = w
                    buffer.get(data, channelOffset, length)
                    channelOffset += length
                } else {
                    length = (w - 1) * pixelStride + 1
                    buffer.get(rowData, 0, length)
                    for (col in 0 until w) {
                        data[channelOffset] = rowData[col * pixelStride]
                        channelOffset += outputStride
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
        }
        return data
    }

    /**
     * Get bytes from original Image which you can get from ImageReader
     * https://www.jianshu.com/p/da10007797b1
     *
     * Return data in YYYYYYYYUVUV(NV12)
     */
    fun getBytesFromImage(image: Image): ByteArray {
        val planes = image.planes
        var imageBytesLength = 0
        for (plane in planes) {
            imageBytesLength += plane.buffer.remaining()
        }
        val imageBytes = ByteArray(imageBytesLength)
        var offset = 0
        for (plane in planes) {
            val buffer = plane.buffer
            val remain = buffer.remaining()
            buffer.get(imageBytes, offset, remain)
            offset += remain
        }
        return imageBytes
    }

    // -----------------------------------------------

    // The input imageBytes is in YYYYYYYYUVUV(NV12)
    // Return data in YYYYYYYYUVUV(NV12)
    fun rotateYUV420Degree90(imageBytes: ByteArray, imageWidth: Int, imageHeight: Int): ByteArray {
        val yuv = ByteArray(imageWidth * imageHeight * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) shr 3)
        // Rotate the Y luma
        var i = 0
        for (x in 0 until imageWidth) {
            for (y in imageHeight - 1 downTo 0) {
                yuv[i++] = imageBytes[y * imageWidth + x]
            }
        }
        // Rotate the U and V color components
        i = yuv.size - 1
        for (x in imageWidth - 1 downTo 0 step 2) {
            for (y in 0 until (imageHeight shr 1)) {
                yuv[i--] = imageBytes[imageWidth * imageHeight + y * imageWidth + x]
                yuv[i--] = imageBytes[imageWidth * imageHeight + y * imageWidth + (x - 1)]
            }
        }
        return yuv
    }

    @Suppress("unused")
    fun rotateYUV420Degree270(imageBytes: ByteArray, imageWidth: Int, imageHeight: Int): ByteArray {
        val yuv = ByteArray(imageWidth * imageHeight * 3 / 2)
        // Rotate the Y luma
        var i = 0
        for (x in imageWidth - 1 downTo 0) {
            for (y in 0 until imageHeight) {
                yuv[i] = imageBytes[y * imageWidth + x]
                i++
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight
        var x = imageWidth - 1
        while (x > 0) {
            for (y in 0 until imageHeight / 2) {
                yuv[i] = imageBytes[imageWidth * imageHeight + y * imageWidth + (x - 1)]
                i++
                yuv[i] = imageBytes[imageWidth * imageHeight + y * imageWidth + x]
                i++
            }
            x -= 2
        }
        return yuv
    }

    fun rotateYUVDegree270AndMirror(imageBytes: ByteArray, imageWidth: Int, imageHeight: Int): ByteArray {
        val yuv = ByteArray(imageWidth * imageHeight * 3 / 2)
        // Rotate and mirror the Y luma
        var i = 0
        var maxY: Int
        for (x in imageWidth - 1 downTo 0) {
            maxY = imageWidth * (imageHeight - 1) + x * 2
            for (y in 0 until imageHeight) {
                yuv[i] = imageBytes[maxY - (y * imageWidth + x)]
                i++
            }
        }
        // Rotate and mirror the U and V color components
        val uvSize = imageWidth * imageHeight
        i = uvSize
        var maxUV: Int
        var x = imageWidth - 1
        while (x > 0) {
            maxUV = imageWidth * (imageHeight / 2 - 1) + x * 2 + uvSize
            for (y in 0 until imageHeight / 2) {
                yuv[i] = imageBytes[maxUV - 2 - (y * imageWidth + x - 1)]
                i++
                yuv[i] = imageBytes[maxUV - (y * imageWidth + x)]
                i++
            }
            x -= 2
        }
        return yuv
    }

    @Suppress("unused")
    fun rotateYUV420Degree180(imageBytes: ByteArray, imageWidth: Int, imageHeight: Int): ByteArray {
        val yuv = ByteArray(imageWidth * imageHeight * 3 / 2)
        var count = 0

        for (i in imageWidth * imageHeight - 1 downTo 0) {
            yuv[count] = imageBytes[i]
            count++
        }

        for (j in imageWidth * imageHeight * 3 / 2 - 1 downTo (imageWidth * imageHeight) step 2) {
            yuv[count++] = imageBytes[j - 1]
            yuv[count++] = imageBytes[j]
        }
        return yuv
    }

    // -----------------------------------------------
    // Works in ImageFormat.JPEG for generating Bitmap
    @Suppress("unused")
    fun generateFromImage(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        buffer.rewind()
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    // Works - Convert 420_888 to NV21 directly, so that we can use generated bytes to encode into H264
    @Suppress("unused")
    fun convertYUV420888ToNV21(image: Image): ByteArray {
// Converting YUV_420_888 data to YUV_420_SP (NV21).
        val data: ByteArray
        val buffer0 = image.planes[0].buffer
        val buffer2 = image.planes[2].buffer
        val buffer0Size = buffer0.remaining()
        val buffer2Size = buffer2.remaining()
        data = ByteArray(buffer0Size + buffer2Size)
        buffer0.get(data, 0, buffer0Size)
        buffer2.get(data, buffer0Size, buffer2Size)
        return data
    }

    // ============================================================
    // Lens back. 90 degree clockwise rotation.
    // Input yuvSrc is in YYYYYYYYUUVV
    fun yuvRotate90(yuvSrc: ByteArray, width: Int, height: Int): ByteArray {
        val desYuv = ByteArray(width * height * 3 / 2)

        var n = 0
        val hw = width / 2
        val hh = height / 2
        //copy y
        for (j in 0 until width) {
            for (i in height - 1 downTo 0) {
                desYuv[n++] = yuvSrc[width * i + j]
            }
        }

        //copy u
        for (j in 0 until hw) {
            for (i in hh - 1 downTo 0) {
                desYuv[n++] = yuvSrc[width * height + hw * i + j]
            }
        }

        //copy v
        for (j in 0 until hw) {
            for (i in hh - 1 downTo 0) {
                desYuv[n++] = yuvSrc[width * height + width * height / 4 + hw * i + j]
            }
        }

        return desYuv
    }

    // Lens front. 270 degree clockwise rotation AKA 90 degree anticlockwise rotation
    @Suppress("unused")
    fun yuvRotate270(yuvSrc: ByteArray, width: Int, height: Int): ByteArray {
        val desYuv = ByteArray(width * height * 3 / 2)
        var n = 0
        val hw = width / 2
        val hh = height / 2
        //copy y
        for (j in width downTo 1) {
            for (i in 0 until height) {
                desYuv[n++] = yuvSrc[width * i + j]
            }
        }

        //copy u
        for (j in hw - 1 downTo 0) {
            for (i in 0 until hh) {
                desYuv[n++] = yuvSrc[width * height + hw * i + j]
            }
        }

        //copy v
        for (j in hw - 1 downTo 0) {
            for (i in 0 until hh) {
                desYuv[n++] = yuvSrc[width * height + width * height / 4 + hw * i + j]
            }
        }

        return desYuv
    }

    /**
     * Horizontal flip the data. For lens front.
     *
     * For front camera, this method must be followed by [yuvRotate90]
     *
     * Example:
     * ```kotlin
     * YuvUtil.yuvRotate90(YuvUtil.yuvFlipHorizontal(yuvData, width, height), width, height)
     * ```
     *
     * @param yuvSrc The YUV data
     * @param width The preview width
     * @param height The preview height
     *
     * @return The horizontal flipped YUV data
     */
    @Suppress("unused")
    fun yuvFlipHorizontal(yuvSrc: ByteArray, width: Int, height: Int): ByteArray {
        val desYuv = ByteArray(width * height * 3 / 2)

        var n = 0
        val hw = width / 2
        val hh = height / 2
        //copy y
        for (j in 0 until height) {
            for (i in width - 1 downTo 0) {
                desYuv[n++] = yuvSrc[width * j + i]
            }
        }

        //copy u
        for (j in 0 until hh) {
            for (i in hw - 1 downTo 0) {
                desYuv[n++] = yuvSrc[width * height + hw * j + i]
            }
        }

        //copy v
        for (j in 0 until hh) {
            for (i in hw - 1 downTo 0) {
                desYuv[n++] = yuvSrc[width * height + width * height / 4 + hw * j + i]
            }
        }

        return desYuv
    }

    // -----------------------------------------------------------------

    @Suppress("unused")
    fun mirrorNv21(yuvSrc: ByteArray, w: Int, h: Int) {
        var temp: Byte
        var a: Int
        var b: Int
        //mirror y
        var i = 0
        while (i < h) {
            a = i * w
            b = (i + 1) * w - 1
            while (a < b) {
                temp = yuvSrc[a]
                yuvSrc[a] = yuvSrc[b]
                yuvSrc[b] = temp
                a++
                b--
            }
            i++
        }

        // mirror u and v
        val index = w * h
        i = 0
        while (i < h / 2) {
            a = i * w
            b = (i + 1) * w - 2
            while (a < b) {
                temp = yuvSrc[a + index]
                yuvSrc[a + index] = yuvSrc[b + index]
                yuvSrc[b + index] = temp
                temp = yuvSrc[a + index + 1]
                yuvSrc[a + index + 1] = yuvSrc[b + index + 1]
                yuvSrc[b + index + 1] = temp
                a += 2
                b -= 2
            }
            i++
        }
    }

    // Not tested
    @Suppress("unused")
    fun mirrorI420(yuvSrc: ByteArray, w: Int, h: Int) {
        var temp: Byte
        var a: Int
        var b: Int
        //mirror y
        for (i in 0 until h) {
            a = i * w
            b = (i + 1) * w - 1
            while (a < b) {
                temp = yuvSrc[a]
                yuvSrc[a] = yuvSrc[b]
                yuvSrc[b] = temp
                a++
                b--
            }
        }
        //mirror u
        var index: Int = w * h//U起始位置
        for (i in 0 until h / 2) {
            a = i * w / 2
            b = (i + 1) * w / 2 - 1
            while (a < b) {
                temp = yuvSrc[a + index]
                yuvSrc[a + index] = yuvSrc[b + index]
                yuvSrc[b + index] = temp
                a++
                b--
            }
        }
        //mirror v
        index = w * h / 4 * 5//V起始位置
        for (i in 0 until h / 2) {
            a = i * w / 2
            b = (i + 1) * w / 2 - 1
            while (a < b) {
                temp = yuvSrc[a + index]
                yuvSrc[a + index] = yuvSrc[b + index]
                yuvSrc[b + index] = temp
                a++
                b--
            }
        }
    }

    @Suppress("unused")
    fun frameMirror(data: ByteArray, width: Int, height: Int) {
        var tempData: Byte
        for (i in 0 until height * 3 / 2) {
            for (j in 0 until width / 2) {
                tempData = data[i * width + j]
                data[i * width + j] = data[(i + 1) * width - 1 - j]
                data[(i + 1) * width - 1 - j] = tempData
            }
        }
    }

    fun rgb2YCbCr420(byteRgba: ByteArray, yuv: ByteArray, width: Int, height: Int) {
        val len = width * height
        var r: Int
        var g: Int
        var b: Int
        var y: Int
        var u: Int
        var v: Int
        var c: Int
        for (i in 0 until height) {
            for (j in 0 until width) {
                c = (i * width + j) * 4
                r = byteRgba[c].toInt() and 0xFF
                g = byteRgba[c + 1].toInt() and 0xFF
                b = byteRgba[c + 2].toInt() and 0xFF
                y = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                u = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                v = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128
                y = if (y < 16) 16 else if (y > 255) 255 else y
                u = if (u < 0) 0 else if (u > 255) 255 else u
                v = if (v < 0) 0 else if (v > 255) 255 else v
                yuv[i * width + j] = y.toByte()
                yuv[len + (i shr 1) * width + (j and 1.inv()) + 0] = u.toByte()
                yuv[len + +(i shr 1) * width + (j and 1.inv()) + 1] = v.toByte()
            }
        }
    }

    //
    //    // ================================
    //
    //    // Basically, it works. But the color of graphic is not correct.
    //    // https://www.jianshu.com/p/1a0f43813433
    //    private static byte[] I4202Nv21(byte[] data, int width, int height) {
    //        byte[] ret = new byte[data.length];
    //        int total = width * height;
    //
    //        ByteBuffer bufferY = ByteBuffer.wrap(ret, 0, total);
    //        ByteBuffer bufferV = ByteBuffer.wrap(ret, total, total / 4);
    //        ByteBuffer bufferU = ByteBuffer.wrap(ret, total + total / 4, total / 4);
    //
    //        bufferY.put(data, 0, total);
    //        for (int i = 0; i < total / 4; i += 1) {
    //            bufferV.put(data[total + i]);
    //            bufferU.put(data[i + total + total / 4]);
    //        }
    //
    //        return ret;
    //    }
    //
    //    // https://stackoverflow.com/q/46403934
    //    private static Bitmap YUV_420_888_toRGBIntrinsics(Context context, int width, int height, byte[] yuv) {
    //        RenderScript rs = RenderScript.create(context);
    //        ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
    //
    //        Type.Builder yuvType = new Type.Builder(rs, Element.U8(rs)).setX(yuv.length);
    //        Allocation in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);
    //
    //        Type.Builder rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height);
    //        Allocation out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);
    //
    //
    //        Bitmap bmpOut = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    //
    //        in.copyFromUnchecked(yuv);
    //
    //        yuvToRgbIntrinsic.setInput(in);
    //        yuvToRgbIntrinsic.forEach(out);
    //        out.copyTo(bmpOut);
    //        return bmpOut;
    //    }
    //
    //
    //    private static void rotateYUV240SP(byte[] src, byte[] des, int width, int height) {
    //        int wh = width * height;
    //        // RotateY
    //        int k = 0;
    //        for (int i = 0; i < width; i++) {
    //            for (int j = 0; j < height; j++) {
    //                des[k] = src[width * j + i];
    //                k++;
    //            }
    //        }
    //
    //        for (int i = 0; i < width; i += 2) {
    //            for (int j = 0; j < height / 2; j++) {
    //                des[k] = src[wh + width * j + i];
    //                des[k + 1] = src[wh + width * j + i + 1];
    //                k += 2;
    //            }
    //        }
    //    }
    //
    //    // Very slow
    //    // https://blog.csdn.net/tklwj/article/details/87975528?depth_1-utm_source=distribute.pc_relevant.none-task&utm_source=distribute.pc_relevant.none-task
    //    private static byte[] rotate180(byte[] src, int width, int height) {
    //        int top = 0;
    //        int bottom = height - 1;
    //        while (top < bottom) {
    //            for (int i = 0; i < width; i++) {
    //                byte b = src[bottom * width + width - 1 - i];
    //                src[bottom * width + width - 1 - i] = src[top * width + i];
    //                src[top * width + i] = b;
    //            }
    //            top++;
    //            bottom--;
    //        }
    //        int uHeader = width * height;
    //        top = 0;
    //        bottom = height / 2 - 1;
    //        while (top < bottom) {
    //            for (int i = 0; i < width; i += 2) {
    //                byte b = src[uHeader + bottom * width + width - 2 - i];
    //                src[uHeader + bottom * width + width - 2 - i] = src[uHeader + top * width + i];
    //                src[uHeader + top * width + i] = b;
    //
    //                b = src[uHeader + bottom * width + width - 1 - i];
    //                src[uHeader + bottom * width + width - 1 - i] = src[uHeader + top * width + i + 1];
    //                src[uHeader + top * width + i + 1] = b;
    //            }
    //            top++;
    //            bottom--;
    //        }
    //        return src;
    //    }
    //
    //    // Very slow
    //    private static byte[] convertYUV420ToNV21_ALL_PLANES(Image imgYUV420) {
    //        assert (imgYUV420.getFormat() == ImageFormat.YUV_420_888);
    //        LogContext.log.d(TAG, "image: " + imgYUV420.getWidth() + "x" + imgYUV420.getHeight() + " " + imgYUV420.getFormat());
    //        LogContext.log.d(TAG, "planes: " + imgYUV420.getPlanes().length);
    //        for (int nPlane = 0; nPlane < imgYUV420.getPlanes().length; nPlane++) {
    //            LogContext.log.d(TAG, "plane[" + nPlane + "]: length " + imgYUV420.getPlanes()[nPlane].getBuffer().remaining() + ", strides: " + imgYUV420.getPlanes()[nPlane].getPixelStride() + " " + imgYUV420.getPlanes()[nPlane].getRowStride());
    //        }
    //
    //        byte[] rez = new byte[imgYUV420.getWidth() * imgYUV420.getHeight() * 3 / 2];
    //        ByteBuffer buffer0 = imgYUV420.getPlanes()[0].getBuffer();
    //        ByteBuffer buffer1 = imgYUV420.getPlanes()[1].getBuffer();
    //        ByteBuffer buffer2 = imgYUV420.getPlanes()[2].getBuffer();
    //
    //        int n = 0;
    //        assert (imgYUV420.getPlanes()[0].getPixelStride() == 1);
    //        for (int row = 0; row < imgYUV420.getHeight(); row++) {
    //            for (int col = 0; col < imgYUV420.getWidth(); col++) {
    //                rez[n++] = buffer0.get();
    //            }
    //        }
    //        assert (imgYUV420.getPlanes()[2].getPixelStride() == imgYUV420.getPlanes()[1].getPixelStride());
    //        int stride = imgYUV420.getPlanes()[1].getPixelStride();
    //        for (int row = 0; row < imgYUV420.getHeight(); row += 2) {
    //            for (int col = 0; col < imgYUV420.getWidth(); col += 2) {
    //                rez[n++] = buffer1.get();
    //                rez[n++] = buffer2.get();
    //                for (int skip = 1; skip < stride; skip++) {
    //                    if (buffer1.remaining() > 0) {
    //                        buffer1.get();
    //                    }
    //                    if (buffer2.remaining() > 0) {
    //                        buffer2.get();
    //                    }
    //                }
    //            }
    //        }
    //
    //        LogContext.log.w(TAG, "total: " + rez.length);
    //        return rez;
    //    }
    //
    //    // Very slow
    //    // https://stackoverflow.com/a/45014917
    //    private static byte[] rotateYUV420ToNV21(Image imgYUV420) {
    //
    //        LogContext.log.d(TAG, "image: " + imgYUV420.getWidth() + "x" + imgYUV420.getHeight() + " " + imgYUV420.getFormat());
    //        LogContext.log.d(TAG, "planes: " + imgYUV420.getPlanes().length);
    //        for (int nPlane = 0; nPlane < imgYUV420.getPlanes().length; nPlane++) {
    //            LogContext.log.d(TAG, "plane[" + nPlane + "]: length " + imgYUV420.getPlanes()[nPlane].getBuffer().remaining() + ", strides: " + imgYUV420.getPlanes()[nPlane].getPixelStride() + " " + imgYUV420.getPlanes()[nPlane].getRowStride());
    //        }
    //
    //        byte[] rez = new byte[imgYUV420.getWidth() * imgYUV420.getHeight() * 3 / 2];
    //        ByteBuffer buffer0 = imgYUV420.getPlanes()[0].getBuffer();
    //        ByteBuffer buffer1 = imgYUV420.getPlanes()[1].getBuffer();
    //        ByteBuffer buffer2 = imgYUV420.getPlanes()[2].getBuffer();
    //
    //        int width = imgYUV420.getHeight();
    //        assert (imgYUV420.getPlanes()[0].getPixelStride() == 1);
    //        for (int row = imgYUV420.getHeight() - 1; row >= 0; row--) {
    //            for (int col = 0; col < imgYUV420.getWidth(); col++) {
    //                rez[col * width + row] = buffer0.get();
    //            }
    //        }
    //        int uv_offset = imgYUV420.getWidth() * imgYUV420.getHeight();
    //        assert (imgYUV420.getPlanes()[2].getPixelStride() == imgYUV420.getPlanes()[1].getPixelStride());
    //        int stride = imgYUV420.getPlanes()[1].getPixelStride();
    //        for (int row = imgYUV420.getHeight() - 2; row >= 0; row -= 2) {
    //            for (int col = 0; col < imgYUV420.getWidth(); col += 2) {
    //                rez[uv_offset + col / 2 * width + row] = buffer1.get();
    //                rez[uv_offset + col / 2 * width + row + 1] = buffer2.get();
    //                for (int skip = 1; skip < stride; skip++) {
    //                    if (buffer1.remaining() > 0) {
    //                        buffer1.get();
    //                    }
    //                    if (buffer2.remaining() > 0) {
    //                        buffer2.get();
    //                    }
    //                }
    //            }
    //        }
    //
    //        LogContext.log.w(TAG, "total rotated: " + rez.length);
    //        return rez;
    //    }
    //
    //    // Seems doesn't work
    //    private static byte[] rotateNV21_working(final byte[] yuv,
    //                                             final int width,
    //                                             final int height,
    //                                             final int rotation) {
    //        if (rotation == 0) return yuv;
    //        if (rotation % 90 != 0 || rotation < 0 || rotation > 270) {
    //            throw new IllegalArgumentException("0 <= rotation < 360, rotation % 90 == 0");
    //        }
    //
    //        final byte[] output = new byte[yuv.length];
    //        final int frameSize = width * height;
    //        final boolean swap = rotation % 180 != 0;
    //        final boolean xFlip = rotation % 270 != 0;
    //        final boolean yFlip = rotation >= 180;
    //
    //        for (int j = 0; j < height; j++) {
    //            for (int i = 0; i < width; i++) {
    //                final int yIn = j * width + i;
    //                final int uIn = frameSize + (j >> 1) * width + (i & ~1);
    //                final int vIn = uIn + 1;
    //
    //                final int wOut = swap ? height : width;
    //                final int hOut = swap ? width : height;
    //                final int iSwapped = swap ? j : i;
    //                final int jSwapped = swap ? i : j;
    //                final int iOut = xFlip ? wOut - iSwapped - 1 : iSwapped;
    //                final int jOut = yFlip ? hOut - jSwapped - 1 : jSwapped;
    //
    //                final int yOut = jOut * wOut + iOut;
    //                final int uOut = frameSize + (jOut >> 1) * wOut + (iOut & ~1);
    //                final int vOut = uOut + 1;
    //
    //                output[yOut] = (byte) (0xff & yuv[yIn]);
    //                output[uOut] = (byte) (0xff & yuv[uIn]);
    //                output[vOut] = (byte) (0xff & yuv[vIn]);
    //            }
    //        }
    //        return output;
    //    }
    //
    //    // https://stackoverflow.com/a/52740776
    //    private static byte[] YUV_420_888toNV21(Image image) {
    //        int width = image.getWidth();
    //        int height = image.getHeight();
    //        int ySize = width * height;
    //        int uvSize = width * height / 4;
    //
    //        byte[] nv21 = new byte[ySize + uvSize * 2];
    //
    //        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
    //        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
    //        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V
    //
    //        int rowStride = image.getPlanes()[0].getRowStride();
    //        assert (image.getPlanes()[0].getPixelStride() == 1);
    //
    //        int pos = 0;
    //
    //        if (rowStride == width) { // likely
    //            yBuffer.get(nv21, 0, ySize);
    //            pos += ySize;
    //        } else {
    //            int yBufferPos = width - rowStride; // not an actual position
    //            for (; pos < ySize; pos += width) {
    //                yBufferPos += rowStride - width;
    //                yBuffer.position(yBufferPos);
    //                yBuffer.get(nv21, pos, width);
    //            }
    //        }
    //
    //        rowStride = image.getPlanes()[2].getRowStride();
    //        int pixelStride = image.getPlanes()[2].getPixelStride();
    //
    //        assert (rowStride == image.getPlanes()[1].getRowStride());
    //        assert (pixelStride == image.getPlanes()[1].getPixelStride());
    //
    //        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
    //            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
    //            byte savePixel = vBuffer.get(1);
    //            try {
    //                vBuffer.put(1, (byte) ~savePixel);
    //                if (uBuffer.get(0) == (byte) ~savePixel) {
    //                    vBuffer.put(1, savePixel);
    //                    vBuffer.get(nv21, ySize, uvSize);
    //
    //                    return nv21; // shortcut
    //                }
    //            } catch (ReadOnlyBufferException ex) {
    //                // unfortunately, we cannot check if vBuffer and uBuffer overlap
    //            }
    //
    //            // unfortunately, the check failed. We must save U and V pixel by pixel
    //            vBuffer.put(1, savePixel);
    //        }
    //
    //        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
    //        // but performance gain would be less significant
    //
    //        for (int row = 0; row < height / 2; row++) {
    //            for (int col = 0; col < width / 2; col++) {
    //                int vuPos = col * pixelStride + row * rowStride;
    //                nv21[pos++] = vBuffer.get(vuPos);
    //                nv21[pos++] = uBuffer.get(vuPos);
    //            }
    //        }
    //
    //        return nv21;
    //    }
    //
    //    // ========================
    //
    //    // Basically, it works. But the color of graphic is not correct.
    //    private static byte[] rotateYUV420SP90(byte[] src, int width, int height) {
    //        int wh = width * height;
    //        byte[] yuv = new byte[wh * 3 >> 1];
    //        // Rotate Y
    //        int count = 0;
    //        for (int i = 0; i < width; i++) {
    //            for (int j = 0; j < height; j++) {
    //                yuv[count++] = src[width * (j + 1) - 1 + i];
    //            }
    //        }
    //        for (int i = 0; i < width; i += 2) {
    //            for (int j = 0, len = height >> 1, index = 0; j < len; j++) {
    //                index = wh + width * j + i;
    //                yuv[count] = src[index];
    //                yuv[count + 1] = src[index + 1];
    //                count += 2;
    //            }
    //        }
    //        return yuv;
    //    }
}