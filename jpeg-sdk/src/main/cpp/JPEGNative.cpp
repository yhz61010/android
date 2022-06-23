#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/log.h>
#include <csetjmp>

#ifdef __cplusplus
extern "C" {
#endif

#include "include/jpeglib.h"
#include "include/turbojpeg.h"

#ifdef __cplusplus
}
#endif

#define LOG_TAG "LEO-JPEG"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define JPEG_PACKAGE_BASE "com/leovp/jpeg_sdk/"

typedef uint8_t BYTE;

struct my_error_mgr {
    struct jpeg_error_mgr pub;
    jmp_buf setjmp_buffer; /* for return to caller */
};
typedef struct my_error_mgr *my_error_ptr;

METHODDEF(void) my_error_exit(j_common_ptr cinfo) {
    auto myerr = (my_error_ptr) cinfo->err;
    (*cinfo->err->output_message)(cinfo);
    LOGE("jpeg_message_table[%d]:%s", myerr->pub.msg_code,
         myerr->pub.jpeg_message_table[myerr->pub.msg_code]);
    longjmp(myerr->setjmp_buffer, 1);
}


int write_JPEG_file(BYTE *data, uint32_t w, uint32_t h, int quality,
                    const char *outFilename, jboolean optimize) {
    //jpeg的结构体，保存的比如宽、高、位深、图片格式等信息
    struct jpeg_compress_struct cinfo{};

    /* Step 1: allocate and initialize JPEG compression object */

    /* We set up the normal JPEG error routines, then override error_exit. */
    struct my_error_mgr jem{};
    cinfo.err = jpeg_std_error(&jem.pub);
    jem.pub.error_exit = my_error_exit;
    /* Establish the setjmp return context for my_error_exit to use. */
    if (setjmp(jem.setjmp_buffer)) {
        /* If we get here, the JPEG code has signaled an error.
         and return.
         */
        return -1;
    }
    jpeg_create_compress(&cinfo);

    /* Step 2: specify data destination (eg, a file) */

    FILE *outfile = fopen(outFilename, "wb");
    if (outfile == nullptr) {
        LOGE("can't open %s", outFilename);
        return -1;
    }
    jpeg_stdio_dest(&cinfo, outfile);

    /* Step 3: set parameters for compression */

    cinfo.image_width = w;      /* image width and height, in pixels */
    cinfo.image_height = h;
    cinfo.input_components = 3;           /* # of color components per pixel */
    cinfo.in_color_space = JCS_RGB;       /* colorspace of input image */

    /*  源码地址：
      [http://androidos.net.cn/androidossearch?query=SkImageDecoder_libjpeg.cpp](http://androidos.net.cn/androidossearch?query=SkImageDecoder_libjpeg.cpp)

      >=android 7.0 后的源码已经设置为true了
      ...省略其它代码
      Tells libjpeg-turbo to compute optimal Huffman coding tables
      for the image.  This improves compression at the cost of
      slower encode performance.
      cinfo.optimize_coding = TRUE;
      jpeg_set_quality(&cinfo, quality, TRUE);
      ...省略其它代码*/


    cinfo.optimize_coding = optimize;
    //哈夫曼编码和算术编码，TRUE=arithmetic coding, FALSE=Huffman
    if (optimize) {
        cinfo.arith_code = false;
    } else {
        cinfo.arith_code = true;
    }
    // 其它参数 全部设置默认参数
    jpeg_set_defaults(&cinfo);
    //设置质量
    jpeg_set_quality(&cinfo, quality, TRUE /* limit to baseline-JPEG values */);

    /* Step 4: Start compressor */

    jpeg_start_compress(&cinfo, TRUE);


    /* Step 5: while (scan lines remain to be written) */
    /*           jpeg_write_scanlines(...); */

    JSAMPROW row_pointer[1];
    uint32_t row_stride;
    //一行的RGB数量
    row_stride = cinfo.image_width * 3; /* JSAMPLEs per row in image_buffer */
    //一行一行遍历
    while (cinfo.next_scanline < cinfo.image_height) {
        //得到一行的首地址
        row_pointer[0] = &data[cinfo.next_scanline * row_stride];
        //此方法会将jcs.next_scanline加1
        jpeg_write_scanlines(&cinfo, row_pointer, 1);//row_pointer就是一行的首地址，1：写入的行数
    }
    /* Step 6: Finish compression */
    jpeg_finish_compress(&cinfo);
    /* After finish_compress, we can close the output file. */
    fclose(outfile);

    /* Step 7: release JPEG compression object */

    /* This is an important step since it will release a good deal of memory. */
    jpeg_destroy_compress(&cinfo);

    /* And we're done! */
    return 0;
}

JNIEXPORT jint JNICALL compressBitmap(JNIEnv *env, __attribute__((unused)) jobject,
                                      jobject bitmap,
                                      jint quality,
                                      jstring outFilPath,
                                      jboolean optimize) {
    //获取Bitmap信息
    AndroidBitmapInfo android_bitmap_info;
    AndroidBitmap_getInfo(env, bitmap, &android_bitmap_info);
    //获取bitmap的 宽，高，format
    uint32_t w = android_bitmap_info.width;
    uint32_t h = android_bitmap_info.height;

//    LOGE("bitmap w=%d h=%d", w, h);

    //读取Bitmap所有像素信息
    BYTE *pixelsColor;
    AndroidBitmap_lockPixels(env, bitmap, (void **) &pixelsColor);

    int i, j;
    BYTE r, g, b;
    //存储RGB所有像素点
    BYTE *data = (BYTE *) malloc(w * h * 3);
    // 临时保存指向像素内存的首地址
    BYTE *tempData = data;

    uint32_t color;
    for (i = 0; i < h; i++) {
        for (j = 0; j < w; j++) {
            // 取出一个像素  去调了alpha，然后保存到data中，对应指针++
            color = *((uint32_t *) pixelsColor);

            // 在jni层中，Bitmap像素点的值是ABGR，而不是ARGB，也就是说，高端到低端：A，B，G，R
            b = ((color & 0x00FF0000) >> 16);
            g = ((color & 0x0000FF00) >> 8);
            r = ((color & 0x000000FF));

            // jpeg压缩需要的是rgb
            //  for example, R,G,B,R,G,B,R,G,B,... for 24-bit RGB color.
            *data = r;
            *(data + 1) = g;
            *(data + 2) = b;
            data += 3;
            pixelsColor += 4;
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    char *path = (char *) env->GetStringUTFChars(outFilPath, nullptr);
    LOGE("path=%s", path);

    // Libjpeg进行压缩
    int resultCode = write_JPEG_file(tempData, w, h, quality, path, optimize);
    if (resultCode == -1) {
        return -1;
    }
    env->ReleaseStringUTFChars(outFilPath, path);
    free(tempData);
    return 0;
}

// =============================

static JNINativeMethod methods[] = {
        {"compressBitmap", "(Landroid/graphics/Bitmap;ILjava/lang/String;Z)I",
         (void *) compressBitmap},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, __attribute__((unused)) void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clz = env->FindClass(JPEG_PACKAGE_BASE"JPEGUtil");
    if (clz == nullptr) {
        return JNI_ERR;
    }

    if (env->RegisterNatives(clz, methods, sizeof(methods) / sizeof(methods[0]))) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}