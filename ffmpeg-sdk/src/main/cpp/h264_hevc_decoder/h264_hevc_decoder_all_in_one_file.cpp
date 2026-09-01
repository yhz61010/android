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
    bool drainStarted = false;
    bool drainFinished = false;
};

namespace {

jclass g_decoder_class = nullptr;
jclass g_decode_info_class = nullptr;
jclass g_decoded_frame_class = nullptr;
jclass g_array_list_class = nullptr;
jfieldID g_native_handle_field = nullptr;
jmethodID g_decode_info_constructor = nullptr;
jmethodID g_decoded_frame_constructor = nullptr;
jmethodID g_array_list_constructor = nullptr;
jmethodID g_array_list_add = nullptr;

void ThrowException(JNIEnv *env, const char *class_name, const char *message) {
    if (env->ExceptionCheck()) return;
    jclass clazz = env->FindClass(class_name);
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message);
        env->DeleteLocalRef(clazz);
    }
}

jclass CacheClass(JNIEnv *env, const char *class_name) {
    jclass local_class = env->FindClass(class_name);
    if (local_class == nullptr) return nullptr;
    jclass global_class = static_cast<jclass>(env->NewGlobalRef(local_class));
    env->DeleteLocalRef(local_class);
    return global_class;
}

void ClearJniCache(JNIEnv *env) {
    if (g_array_list_class != nullptr) env->DeleteGlobalRef(g_array_list_class);
    if (g_decoded_frame_class != nullptr) env->DeleteGlobalRef(g_decoded_frame_class);
    if (g_decode_info_class != nullptr) env->DeleteGlobalRef(g_decode_info_class);
    if (g_decoder_class != nullptr) env->DeleteGlobalRef(g_decoder_class);
    g_array_list_class = nullptr;
    g_decoded_frame_class = nullptr;
    g_decode_info_class = nullptr;
    g_decoder_class = nullptr;
    g_native_handle_field = nullptr;
    g_decode_info_constructor = nullptr;
    g_decoded_frame_constructor = nullptr;
    g_array_list_constructor = nullptr;
    g_array_list_add = nullptr;
}

bool CacheJniReferences(JNIEnv *env) {
    g_decoder_class = CacheClass(env, H264_HEVC_PACKAGE_BASE "video/H264HevcDecoder");
    if (g_decoder_class == nullptr) return false;
    g_native_handle_field = env->GetFieldID(g_decoder_class, "nativeHandle", "J");
    if (g_native_handle_field == nullptr) return false;

    g_decode_info_class = CacheClass(
            env, H264_HEVC_PACKAGE_BASE "video/H264HevcDecoder$DecodeVideoInfo");
    if (g_decode_info_class == nullptr) return false;
    g_decode_info_constructor = env->GetMethodID(
            g_decode_info_class, "<init>", "(ILjava/lang/String;ILjava/lang/String;II)V");
    if (g_decode_info_constructor == nullptr) return false;

    g_decoded_frame_class = CacheClass(
            env, H264_HEVC_PACKAGE_BASE "video/H264HevcDecoder$DecodedVideoFrame");
    if (g_decoded_frame_class == nullptr) return false;
    g_decoded_frame_constructor = env->GetMethodID(
            g_decoded_frame_class, "<init>", "([BIII)V");
    if (g_decoded_frame_constructor == nullptr) return false;

    g_array_list_class = CacheClass(env, "java/util/ArrayList");
    if (g_array_list_class == nullptr) return false;
    g_array_list_constructor = env->GetMethodID(g_array_list_class, "<init>", "()V");
    if (g_array_list_constructor == nullptr) return false;
    g_array_list_add = env->GetMethodID(
            g_array_list_class, "add", "(Ljava/lang/Object;)Z");
    return g_array_list_add != nullptr;
}

jfieldID GetHandleField(JNIEnv *, jobject) {
    return g_native_handle_field;
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

jobject CreateFrameList(JNIEnv *env) {
    jobject result = env->NewObject(g_array_list_class, g_array_list_constructor);
    if (result == nullptr && !env->ExceptionCheck()) {
        ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate decoded frame list");
    }
    return result;
}

jobject CreateDecodedFrame(JNIEnv *env, H264HevcDecoderContext *decoder) {
    AVFrame *frame = decoder->frame;
    const AVPixelFormat output_format = decoder->outputFormat == AV_PIX_FMT_NONE
                                        ? static_cast<AVPixelFormat>(frame->format)
                                        : decoder->outputFormat;
    const int image_size = av_image_get_buffer_size(output_format, frame->width, frame->height, 1);
    if (image_size <= 0) {
        ThrowException(env, "java/lang/IllegalStateException", "Invalid decoded frame dimensions");
        return nullptr;
    }
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
                ThrowException(env, "java/lang/IllegalStateException",
                               "Unable to create pixel converter");
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
    if (written_bytes <= 0) {
        ThrowException(env, "java/lang/IllegalStateException", "Unable to copy decoded frame");
        return nullptr;
    }

    jbyteArray output = env->NewByteArray(written_bytes);
    if (output == nullptr) {
        if (!env->ExceptionCheck()) {
            ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate decoded output");
        }
        return nullptr;
    }
    env->SetByteArrayRegion(output, 0, written_bytes,
                            reinterpret_cast<const jbyte *>(decoder->imageBuffer));
    if (env->ExceptionCheck()) {
        env->DeleteLocalRef(output);
        return nullptr;
    }
    jobject result = env->NewObject(g_decoded_frame_class, g_decoded_frame_constructor, output,
                                    static_cast<int>(output_format), frame->width, frame->height);
    env->DeleteLocalRef(output);
    if (result == nullptr && !env->ExceptionCheck()) {
        ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate decoded frame");
    }
    return result;
}

int ReceiveFrames(JNIEnv *env, H264HevcDecoderContext *decoder, jobject output,
                  bool *reached_eof) {
    int received = 0;
    *reached_eof = false;
    for (;;) {
        const int result = avcodec_receive_frame(decoder->codecContext, decoder->frame);
        if (result == AVERROR(EAGAIN)) return received;
        if (result == AVERROR_EOF) {
            *reached_eof = true;
            return received;
        }
        if (result < 0) {
            LOGE("Unable to receive decoded video frame. code=%d", result);
            ThrowException(env, "java/lang/IllegalStateException", "Video decoding failed");
            return -1;
        }

        FrameUnrefGuard frame_guard(decoder->frame);
        jobject decoded_frame = CreateDecodedFrame(env, decoder);
        if (decoded_frame == nullptr) return -1;
        env->CallBooleanMethod(output, g_array_list_add, decoded_frame);
        env->DeleteLocalRef(decoded_frame);
        if (env->ExceptionCheck()) return -1;
        ++received;
    }
}

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

    const char *codec_name_chars = avcodec_get_name(decoder->codecContext->codec_id);
    const char *pixel_format_chars = av_get_pix_fmt_name(decoder->codecContext->pix_fmt);
    jstring codec_name = codec_name_chars == nullptr ? nullptr : env->NewStringUTF(codec_name_chars);
    if (env->ExceptionCheck() || (codec_name_chars != nullptr && codec_name == nullptr)) {
        if (!env->ExceptionCheck()) {
            ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate codec name");
        }
        DestroyDecoderContext(decoder);
        return nullptr;
    }
    jstring pixel_format_name = pixel_format_chars == nullptr
                                ? nullptr
                                : env->NewStringUTF(pixel_format_chars);
    if (env->ExceptionCheck() ||
        (pixel_format_chars != nullptr && pixel_format_name == nullptr)) {
        if (!env->ExceptionCheck()) {
            ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate pixel format name");
        }
        if (codec_name != nullptr) env->DeleteLocalRef(codec_name);
        DestroyDecoderContext(decoder);
        return nullptr;
    }
    jobject result = env->NewObject(g_decode_info_class, g_decode_info_constructor,
                                    static_cast<int>(decoder->codecContext->codec_id), codec_name,
                                    static_cast<int>(decoder->codecContext->pix_fmt), pixel_format_name,
                                    decoder->codecContext->width, decoder->codecContext->height);
    if (codec_name != nullptr) env->DeleteLocalRef(codec_name);
    if (pixel_format_name != nullptr) env->DeleteLocalRef(pixel_format_name);
    if (result == nullptr) {
        if (!env->ExceptionCheck()) {
            ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate decoder info");
        }
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

JNIEXPORT jobject JNICALL NativeDecodeFrames(JNIEnv *env, jobject object, jbyteArray encoded_array) {
    H264HevcDecoderContext *decoder = GetDecoderContext(env, object);
    if (decoder == nullptr) {
        ThrowException(env, "java/lang/IllegalStateException", "Decoder is closed");
        return nullptr;
    }
    if (encoded_array == nullptr || env->GetArrayLength(encoded_array) <= 0) {
        ThrowException(env, "java/lang/IllegalArgumentException", "Encoded frame must not be empty");
        return nullptr;
    }
    if (decoder->drainStarted) {
        ThrowException(env, "java/lang/IllegalStateException",
                       "Decoder has reached end of input and cannot accept more packets");
        return nullptr;
    }

    jobject output = CreateFrameList(env);
    if (output == nullptr) return nullptr;

    const jsize encoded_length = env->GetArrayLength(encoded_array);
    av_packet_unref(decoder->packet);
    av_frame_unref(decoder->frame);
    if (av_new_packet(decoder->packet, encoded_length) < 0) {
        env->DeleteLocalRef(output);
        ThrowException(env, "java/lang/OutOfMemoryError", "Unable to allocate video packet");
        return nullptr;
    }
    env->GetByteArrayRegion(encoded_array, 0, encoded_length,
                            reinterpret_cast<jbyte *>(decoder->packet->data));
    if (env->ExceptionCheck()) {
        av_packet_unref(decoder->packet);
        env->DeleteLocalRef(output);
        return nullptr;
    }
    std::memset(decoder->packet->data + encoded_length, 0, AV_INPUT_BUFFER_PADDING_SIZE);

    int send_result;
    for (;;) {
        send_result = avcodec_send_packet(decoder->codecContext, decoder->packet);
        if (send_result != AVERROR(EAGAIN)) break;

        bool reached_eof = false;
        const int received = ReceiveFrames(env, decoder, output, &reached_eof);
        if (received <= 0 || reached_eof || env->ExceptionCheck()) {
            if (!env->ExceptionCheck()) {
                ThrowException(env, "java/lang/IllegalStateException",
                               "Decoder made no progress while accepting input");
            }
            av_packet_unref(decoder->packet);
            env->DeleteLocalRef(output);
            return nullptr;
        }
    }
    if (send_result < 0) {
        LOGE("Unable to send video packet. code=%d", send_result);
        av_packet_unref(decoder->packet);
        env->DeleteLocalRef(output);
        const char *message = send_result == AVERROR_EOF
                              ? "Decoder has already reached end of input"
                              : "Unable to send video packet";
        ThrowException(env, "java/lang/IllegalStateException", message);
        return nullptr;
    }
    av_packet_unref(decoder->packet);

    bool reached_eof = false;
    if (ReceiveFrames(env, decoder, output, &reached_eof) < 0) {
        env->DeleteLocalRef(output);
        return nullptr;
    }
    return output;
}

JNIEXPORT jobject JNICALL NativeDrain(JNIEnv *env, jobject object) {
    H264HevcDecoderContext *decoder = GetDecoderContext(env, object);
    if (decoder == nullptr) {
        ThrowException(env, "java/lang/IllegalStateException", "Decoder is closed");
        return nullptr;
    }

    jobject output = CreateFrameList(env);
    if (output == nullptr) return nullptr;
    if (decoder->drainFinished) return output;

    if (!decoder->drainStarted) {
        for (;;) {
            const int send_result = avcodec_send_packet(decoder->codecContext, nullptr);
            if (send_result == 0) {
                decoder->drainStarted = true;
                break;
            }
            if (send_result == AVERROR_EOF) {
                decoder->drainStarted = true;
                decoder->drainFinished = true;
                return output;
            }
            if (send_result != AVERROR(EAGAIN)) {
                LOGE("Unable to start video decoder drain. code=%d", send_result);
                env->DeleteLocalRef(output);
                ThrowException(env, "java/lang/IllegalStateException",
                               "Unable to drain video decoder");
                return nullptr;
            }

            bool reached_eof = false;
            const int received = ReceiveFrames(env, decoder, output, &reached_eof);
            if (received <= 0 || env->ExceptionCheck()) {
                if (!env->ExceptionCheck()) {
                    ThrowException(env, "java/lang/IllegalStateException",
                                   "Decoder made no progress while starting drain");
                }
                env->DeleteLocalRef(output);
                return nullptr;
            }
            if (reached_eof) {
                decoder->drainStarted = true;
                decoder->drainFinished = true;
                return output;
            }
        }
    }

    bool reached_eof = false;
    if (ReceiveFrames(env, decoder, output, &reached_eof) < 0) {
        env->DeleteLocalRef(output);
        return nullptr;
    }
    if (!reached_eof) {
        env->DeleteLocalRef(output);
        ThrowException(env, "java/lang/IllegalStateException",
                       "Decoder drain ended before end of stream");
        return nullptr;
    }
    decoder->drainFinished = true;
    return output;
}

JNIEXPORT jstring JNICALL NativeGetVersion(JNIEnv *env, jobject) {
    return env->NewStringUTF("1.0.0");
}

static JNINativeMethod methods[] = {
        {"nativeInit", "([B[B[B[B[BI)Lcom/leovp/ffmpeg/video/H264HevcDecoder$DecodeVideoInfo;",
         reinterpret_cast<void *>(NativeInit)},
        {"nativeRelease", "()V", reinterpret_cast<void *>(NativeRelease)},
        {"nativeDecodeFrames", "([B)Ljava/util/List;",
         reinterpret_cast<void *>(NativeDecodeFrames)},
        {"nativeDrain", "()Ljava/util/List;", reinterpret_cast<void *>(NativeDrain)},
        {"nativeGetVersion", "()Ljava/lang/String;", reinterpret_cast<void *>(NativeGetVersion)},
};

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    if (!CacheJniReferences(env)) {
        ClearJniCache(env);
        return JNI_ERR;
    }
    const jint result = env->RegisterNatives(g_decoder_class, methods,
                                             sizeof(methods) / sizeof(methods[0]));
    if (result != JNI_OK) {
        ClearJniCache(env);
        return JNI_ERR;
    }
    if (av_jni_set_java_vm(vm, reserved) < 0) {
        env->UnregisterNatives(g_decoder_class);
        ClearJniCache(env);
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
        ClearJniCache(env);
    }
}
