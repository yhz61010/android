#include <jni.h>

#include <cstdint>
#include <cstring>
#include <limits>
#include <new>

#include "logger.h"

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavcodec/jni.h>
#include <libavutil/imgutils.h>
#include <libavutil/pixdesc.h>
#include <libswscale/swscale.h>
}

#define H264_HEVC_PACKAGE_BASE "com/leovp/ffmpeg/"

struct H264HevcDecoderContext {
    AVCodecContext *codecContext = nullptr;
    AVFrame *frame = nullptr;
    AVPacket *packet = nullptr;
    SwsContext *convertContext = nullptr;
    AVFrame *convertedFrame = nullptr;
    AVPixelFormat outputFormat = AV_PIX_FMT_NONE;
    int lastSwsWidth = 0;
    int lastSwsHeight = 0;
    AVPixelFormat lastSwsSourceFormat = AV_PIX_FMT_NONE;
    uint8_t *imageBuffer = nullptr;
    size_t imageBufferCapacity = 0;
};

namespace {

void ThrowException(JNIEnv *env, const char *class_name, const char *message) {
    if (env->ExceptionCheck()) return;
    jclass clazz = env->FindClass(class_name);
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message);
        env->DeleteLocalRef(clazz);
    }
}

jfieldID GetHandleField(JNIEnv *env, jobject object) {
    jclass clazz = env->GetObjectClass(object);
    if (clazz == nullptr) return nullptr;
    jfieldID field = env->GetFieldID(clazz, "nativeHandle", "J");
    env->DeleteLocalRef(clazz);
    return field;
}

H264HevcDecoderContext *GetDecoderContext(JNIEnv *env, jobject object) {
    jfieldID field = GetHandleField(env, object);
    if (field == nullptr) return nullptr;
    return reinterpret_cast<H264HevcDecoderContext *>(env->GetLongField(object, field));
}

void DestroyDecoderContext(H264HevcDecoderContext *context) {
    if (context == nullptr) return;
    if (context->convertContext != nullptr) sws_freeContext(context->convertContext);
    av_freep(&context->imageBuffer);
    if (context->convertedFrame != nullptr) av_frame_free(&context->convertedFrame);
    if (context->packet != nullptr) av_packet_free(&context->packet);
    if (context->frame != nullptr) av_frame_free(&context->frame);
    if (context->codecContext != nullptr) avcodec_free_context(&context->codecContext);
    delete context;
}

AVPixelFormat ConvertDeprecatedFormat(AVPixelFormat format) {
    switch (format) {
        case AV_PIX_FMT_YUVJ420P:
            return AV_PIX_FMT_YUV420P;
        case AV_PIX_FMT_YUVJ422P:
            return AV_PIX_FMT_YUV422P;
        case AV_PIX_FMT_YUVJ444P:
            return AV_PIX_FMT_YUV444P;
        case AV_PIX_FMT_YUVJ440P:
            return AV_PIX_FMT_YUV440P;
        default:
            return format;
    }
}

AVPixelFormat ResolveOutputFormat(jint rgb_type) {
    switch (rgb_type) {
        case 1:
            return AV_PIX_FMT_BGRA;
        case 2:
            return AV_PIX_FMT_RGBA;
        case 3:
            return AV_PIX_FMT_ARGB;
        case 4:
            return AV_PIX_FMT_ABGR;
        case 5:
            return AV_PIX_FMT_BGR24;
        case 6:
            return AV_PIX_FMT_RGB24;
        default:
            return AV_PIX_FMT_NONE;
    }
}

bool CopyArray(JNIEnv *env, jbyteArray source, uint8_t *destination, size_t *offset) {
    if (source == nullptr) return true;
    const jsize length = env->GetArrayLength(source);
    if (length == 0) return true;
    env->GetByteArrayRegion(source, 0, length,
                            reinterpret_cast<jbyte *>(destination + *offset));
    if (env->ExceptionCheck()) return false;
    *offset += static_cast<size_t>(length);
    return true;
}

bool EnsureImageBuffer(H264HevcDecoderContext *context, size_t required) {
    if (required <= context->imageBufferCapacity) return true;
    void *resized = av_realloc(context->imageBuffer, required);
    if (resized == nullptr) return false;
    context->imageBuffer = static_cast<uint8_t *>(resized);
    context->imageBufferCapacity = required;
    return true;
}

class FrameUnrefGuard {
public:
    explicit FrameUnrefGuard(AVFrame *frame) : frame_(frame) {}

    ~FrameUnrefGuard() {
        av_frame_unref(frame_);
    }

    FrameUnrefGuard(const FrameUnrefGuard &) = delete;
    FrameUnrefGuard &operator=(const FrameUnrefGuard &) = delete;

private:
    AVFrame *frame_;
};

}  // namespace

JNIEXPORT jobject JNICALL NativeInit(JNIEnv *env, jobject object, jbyteArray vps_array,
                                     jbyteArray sps_array, jbyteArray pps_array,
                                     jbyteArray prefix_sei_array, jbyteArray suffix_sei_array,
                                     jint rgb_type) {
    if (GetDecoderContext(env, object) != nullptr) {
        ThrowException(env, "java/lang/IllegalStateException", "Decoder is already initialized");
        return nullptr;
    }
    if (sps_array == nullptr || pps_array == nullptr ||
        env->GetArrayLength(sps_array) == 0 || env->GetArrayLength(pps_array) == 0 ||
        (vps_array != nullptr && env->GetArrayLength(vps_array) == 0)) {
        ThrowException(env, "java/lang/IllegalArgumentException", "SPS/PPS and optional VPS must not be empty");
        return nullptr;
    }

    const AVCodecID codec_id = vps_array == nullptr ? AV_CODEC_ID_H264 : AV_CODEC_ID_HEVC;
    const jbyteArray arrays[] = {
            vps_array, sps_array, pps_array, prefix_sei_array, suffix_sei_array};
    uint64_t csd_length = 0;
    for (jbyteArray array : arrays) {
        if (array != nullptr) csd_length += static_cast<uint64_t>(env->GetArrayLength(array));
    }
    if (csd_length == 0 ||
        csd_length > static_cast<uint64_t>(std::numeric_limits<int>::max()) -
                     AV_INPUT_BUFFER_PADDING_SIZE) {
        ThrowException(env, "java/lang/IllegalArgumentException", "Codec-specific data is too large");
        return nullptr;
    }

    auto *decoder = new(std::nothrow) H264HevcDecoderContext();
    if (decoder == nullptr) {
        ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate decoder context");
        return nullptr;
    }
    const AVCodec *codec = avcodec_find_decoder(codec_id);
    if (codec == nullptr) {
        DestroyDecoderContext(decoder);
        ThrowException(env, "java/lang/IllegalStateException", "Requested video decoder was not found");
        return nullptr;
    }
    decoder->codecContext = avcodec_alloc_context3(codec);
    if (decoder->codecContext == nullptr) {
        DestroyDecoderContext(decoder);
        ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate video codec context");
        return nullptr;
    }
    decoder->codecContext->extradata = static_cast<uint8_t *>(
            av_mallocz(static_cast<size_t>(csd_length) + AV_INPUT_BUFFER_PADDING_SIZE));
    if (decoder->codecContext->extradata == nullptr) {
        DestroyDecoderContext(decoder);
        ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate codec-specific data");
        return nullptr;
    }
    decoder->codecContext->extradata_size = static_cast<int>(csd_length);
    size_t offset = 0;
    for (jbyteArray array : arrays) {
        if (!CopyArray(env, array, decoder->codecContext->extradata, &offset)) {
            DestroyDecoderContext(decoder);
            return nullptr;
        }
    }
    if (avcodec_open2(decoder->codecContext, codec, nullptr) < 0) {
        DestroyDecoderContext(decoder);
        ThrowException(env, "java/lang/IllegalStateException", "Unable to open video decoder");
        return nullptr;
    }

    decoder->frame = av_frame_alloc();
    decoder->packet = av_packet_alloc();
    decoder->outputFormat = ResolveOutputFormat(rgb_type);
    if (decoder->outputFormat != AV_PIX_FMT_NONE) decoder->convertedFrame = av_frame_alloc();
    if (decoder->frame == nullptr || decoder->packet == nullptr ||
        (decoder->outputFormat != AV_PIX_FMT_NONE && decoder->convertedFrame == nullptr)) {
        DestroyDecoderContext(decoder);
        ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate video frame resources");
        return nullptr;
    }

    jclass info_class = env->FindClass(
            H264_HEVC_PACKAGE_BASE "video/H264HevcDecoder$DecodeVideoInfo");
    jmethodID constructor = info_class == nullptr
                            ? nullptr
                            : env->GetMethodID(
                                    info_class, "<init>",
                                    "(ILjava/lang/String;ILjava/lang/String;II)V");
    if (constructor == nullptr || env->ExceptionCheck()) {
        if (info_class != nullptr) env->DeleteLocalRef(info_class);
        DestroyDecoderContext(decoder);
        return nullptr;
    }
    const char *codec_name_chars = avcodec_get_name(decoder->codecContext->codec_id);
    const char *pixel_format_chars = av_get_pix_fmt_name(decoder->codecContext->pix_fmt);
    jstring codec_name = codec_name_chars == nullptr ? nullptr : env->NewStringUTF(codec_name_chars);
    jstring pixel_format_name = pixel_format_chars == nullptr
                                ? nullptr
                                : env->NewStringUTF(pixel_format_chars);
    if (env->ExceptionCheck()) {
        if (codec_name != nullptr) env->DeleteLocalRef(codec_name);
        if (pixel_format_name != nullptr) env->DeleteLocalRef(pixel_format_name);
        env->DeleteLocalRef(info_class);
        DestroyDecoderContext(decoder);
        return nullptr;
    }
    jobject result = env->NewObject(info_class, constructor,
                                    static_cast<int>(decoder->codecContext->codec_id), codec_name,
                                    static_cast<int>(decoder->codecContext->pix_fmt), pixel_format_name,
                                    decoder->codecContext->width, decoder->codecContext->height);
    if (codec_name != nullptr) env->DeleteLocalRef(codec_name);
    if (pixel_format_name != nullptr) env->DeleteLocalRef(pixel_format_name);
    env->DeleteLocalRef(info_class);
    if (result == nullptr || env->ExceptionCheck()) {
        DestroyDecoderContext(decoder);
        return nullptr;
    }

    jfieldID handle_field = GetHandleField(env, object);
    if (handle_field == nullptr) {
        env->DeleteLocalRef(result);
        DestroyDecoderContext(decoder);
        return nullptr;
    }
    env->SetLongField(object, handle_field, reinterpret_cast<jlong>(decoder));
    if (env->ExceptionCheck()) {
        env->DeleteLocalRef(result);
        DestroyDecoderContext(decoder);
        return nullptr;
    }
    return result;
}

JNIEXPORT void JNICALL NativeRelease(JNIEnv *env, jobject object) {
    jfieldID field = GetHandleField(env, object);
    if (field == nullptr) return;
    auto *decoder = reinterpret_cast<H264HevcDecoderContext *>(env->GetLongField(object, field));
    env->SetLongField(object, field, 0L);
    DestroyDecoderContext(decoder);
}

JNIEXPORT jobject JNICALL NativeDecode(JNIEnv *env, jobject object, jbyteArray encoded_array) {
    H264HevcDecoderContext *decoder = GetDecoderContext(env, object);
    if (decoder == nullptr) {
        ThrowException(env, "java/lang/IllegalStateException", "Decoder is closed");
        return nullptr;
    }
    if (encoded_array == nullptr || env->GetArrayLength(encoded_array) <= 0) {
        ThrowException(env, "java/lang/IllegalArgumentException", "Encoded frame must not be empty");
        return nullptr;
    }

    const jsize encoded_length = env->GetArrayLength(encoded_array);
    av_packet_unref(decoder->packet);
    av_frame_unref(decoder->frame);
    if (av_new_packet(decoder->packet, encoded_length) < 0) {
        ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate video packet");
        return nullptr;
    }
    env->GetByteArrayRegion(encoded_array, 0, encoded_length,
                            reinterpret_cast<jbyte *>(decoder->packet->data));
    if (env->ExceptionCheck()) {
        av_packet_unref(decoder->packet);
        return nullptr;
    }
    std::memset(decoder->packet->data + encoded_length, 0, AV_INPUT_BUFFER_PADDING_SIZE);
    if (avcodec_send_packet(decoder->codecContext, decoder->packet) < 0 ||
        avcodec_receive_frame(decoder->codecContext, decoder->frame) < 0) {
        av_packet_unref(decoder->packet);
        return nullptr;
    }
    av_packet_unref(decoder->packet);
    FrameUnrefGuard frame_guard(decoder->frame);

    AVFrame *frame = decoder->frame;
    const AVPixelFormat output_format = decoder->outputFormat == AV_PIX_FMT_NONE
                                        ? static_cast<AVPixelFormat>(frame->format)
                                        : decoder->outputFormat;
    const int image_size = av_image_get_buffer_size(output_format, frame->width, frame->height, 1);
    if (image_size <= 0) return nullptr;
    if (!EnsureImageBuffer(decoder, static_cast<size_t>(image_size))) {
        ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate decoded image buffer");
        return nullptr;
    }

    int written_bytes;
    if (decoder->outputFormat != AV_PIX_FMT_NONE) {
        const AVPixelFormat source_format =
                ConvertDeprecatedFormat(static_cast<AVPixelFormat>(frame->format));
        if (decoder->convertContext == nullptr || decoder->lastSwsWidth != frame->width ||
            decoder->lastSwsHeight != frame->height ||
            decoder->lastSwsSourceFormat != source_format) {
            sws_freeContext(decoder->convertContext);
            decoder->convertContext = sws_getContext(
                    frame->width, frame->height, source_format,
                    frame->width, frame->height, decoder->outputFormat,
                    SWS_POINT, nullptr, nullptr, nullptr);
            if (decoder->convertContext == nullptr) {
                ThrowException(env, "java/lang/IllegalStateException", "Unable to create pixel converter");
                return nullptr;
            }
            decoder->lastSwsWidth = frame->width;
            decoder->lastSwsHeight = frame->height;
            decoder->lastSwsSourceFormat = source_format;
        }
        if (av_image_fill_arrays(decoder->convertedFrame->data,
                                 decoder->convertedFrame->linesize,
                                 decoder->imageBuffer, decoder->outputFormat,
                                 frame->width, frame->height, 1) < 0 ||
            sws_scale(decoder->convertContext, frame->data, frame->linesize, 0,
                      frame->height, decoder->convertedFrame->data,
                      decoder->convertedFrame->linesize) <= 0) {
            ThrowException(env, "java/lang/IllegalStateException", "Pixel conversion failed");
            return nullptr;
        }
        written_bytes = image_size;
    } else {
        written_bytes = av_image_copy_to_buffer(
                decoder->imageBuffer, image_size,
                const_cast<const uint8_t *const *>(frame->data), frame->linesize,
                static_cast<AVPixelFormat>(frame->format), frame->width, frame->height, 1);
    }
    if (written_bytes <= 0) return nullptr;

    jbyteArray output = env->NewByteArray(written_bytes);
    if (output == nullptr) return nullptr;
    env->SetByteArrayRegion(output, 0, written_bytes,
                            reinterpret_cast<const jbyte *>(decoder->imageBuffer));
    if (env->ExceptionCheck()) {
        env->DeleteLocalRef(output);
        return nullptr;
    }
    jclass frame_class = env->FindClass(
            H264_HEVC_PACKAGE_BASE "video/H264HevcDecoder$DecodedVideoFrame");
    jmethodID constructor = frame_class == nullptr
                            ? nullptr
                            : env->GetMethodID(frame_class, "<init>", "([BIII)V");
    if (constructor == nullptr || env->ExceptionCheck()) {
        if (frame_class != nullptr) env->DeleteLocalRef(frame_class);
        env->DeleteLocalRef(output);
        return nullptr;
    }
    jobject result = env->NewObject(frame_class, constructor, output,
                                    static_cast<int>(output_format), frame->width, frame->height);
    env->DeleteLocalRef(frame_class);
    env->DeleteLocalRef(output);
    return result;
}

JNIEXPORT jstring JNICALL NativeGetVersion(JNIEnv *env, jobject) {
    return env->NewStringUTF("1.0.0");
}

static JNINativeMethod methods[] = {
        {"nativeInit", "([B[B[B[B[BI)Lcom/leovp/ffmpeg/video/H264HevcDecoder$DecodeVideoInfo;",
         reinterpret_cast<void *>(NativeInit)},
        {"nativeRelease", "()V", reinterpret_cast<void *>(NativeRelease)},
        {"nativeDecode", "([B)Lcom/leovp/ffmpeg/video/H264HevcDecoder$DecodedVideoFrame;",
         reinterpret_cast<void *>(NativeDecode)},
        {"nativeGetVersion", "()Ljava/lang/String;", reinterpret_cast<void *>(NativeGetVersion)},
};

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass clazz = env->FindClass(H264_HEVC_PACKAGE_BASE "video/H264HevcDecoder");
    if (clazz == nullptr) return JNI_ERR;
    const jint result = env->RegisterNatives(clazz, methods,
                                             sizeof(methods) / sizeof(methods[0]));
    env->DeleteLocalRef(clazz);
    if (result != 0) return JNI_ERR;
    return av_jni_set_java_vm(vm, reserved) >= 0 ? JNI_VERSION_1_6 : JNI_ERR;
}
