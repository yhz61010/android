#include <jni.h>
#include <string>
#include <x264.h>
#include <android/log.h>

#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "x264_jni", __VA_ARGS__))

#define X264A_OK 0
#define X264A_ERR_APPLY_PROFILE -2
#define X264A_ERR_OPEN_ENCODER -3
#define X264A_ERR_NOT_SUPPORT_CPS -4
#define X264A_ERR_ENCODE_FRAME -5
#define X264A_PACKAGE "com/leovp/x264/"

extern "C"

typedef struct EncoderContext {
    x264_param_t params;
    x264_t *encoder;
    x264_picture_t input_picture;
} EncoderContext;

static void set_ctx(JNIEnv *env, jobject thiz, void *ctx) {
    jclass cls = env->GetObjectClass(thiz);
    jfieldID fid = env->GetFieldID(cls, "ctx", "J");
    env->SetLongField(thiz, fid, (jlong) (uintptr_t) ctx);
}

static void *get_ctx(JNIEnv *env, jobject thiz) {
    jclass cls = env->GetObjectClass(thiz);
    jfieldID fid = env->GetFieldID(cls, "ctx", "J");
    return (void *) (uintptr_t) env->GetLongField(thiz, fid);
}

/**
 * Create a new encoder.
 */
JNIEXPORT jobject initEncoder(JNIEnv *env, jobject thiz, jobject params) {
    EncoderContext *ctx = (EncoderContext *) calloc(1, sizeof(EncoderContext));
    set_ctx(env, thiz, ctx);
    jclass rsCls = env->FindClass(X264A_PACKAGE"X264InitResult");
    jmethodID rsInit = env->GetMethodID(rsCls, "<init>", "(I[B[B)V");

    // init params
    jclass paramsCls = env->GetObjectClass(params);

    jstring preset = (jstring) env->GetObjectField
            (params, env->GetFieldID(paramsCls, "preset", "Ljava/lang/String;"));
    const char *c_preset = env->GetStringUTFChars(preset, NULL);
    x264_param_default_preset(&ctx->params, c_preset, "zerolatency");
    env->ReleaseStringUTFChars(preset, c_preset);

    ctx->params.i_width = env->GetIntField(params, env->GetFieldID(paramsCls, "width", "I"));
    ctx->params.i_height = env->GetIntField(params, env->GetFieldID(paramsCls, "height", "I"));
    ctx->params.rc.i_bitrate =
            env->GetIntField(params, env->GetFieldID(paramsCls, "bitrate", "I")) / 1000;
    ctx->params.rc.i_rc_method = X264_RC_ABR;
    ctx->params.i_fps_num = env->GetIntField(params, env->GetFieldID(paramsCls, "fps", "I"));
    ctx->params.i_fps_den = 1;
    ctx->params.i_keyint_max = env->GetIntField(params, env->GetFieldID(paramsCls, "gop", "I"));
    ctx->params.b_repeat_headers = 0;

    jstring profile = (jstring) env->GetObjectField
            (params, env->GetFieldID(paramsCls, "profile", "Ljava/lang/String;"));
    const char *c_profile = env->GetStringUTFChars(profile, NULL);
    int apply_profile = x264_param_apply_profile(&ctx->params, c_profile);
    env->ReleaseStringUTFChars(preset, c_profile);
    if (apply_profile < 0)
        return env->NewObject(rsCls, rsInit, X264A_ERR_APPLY_PROFILE, NULL, NULL);

    ctx->encoder = x264_encoder_open(&ctx->params);
    if (ctx->encoder == NULL)
        return env->NewObject(rsCls, rsInit, X264A_ERR_OPEN_ENCODER, NULL, NULL);

    // return the SPS and PPS that will be used for the whole stream
    int pi_nal;
    x264_nal_t *pp_nal;
    x264_encoder_headers(ctx->encoder, &pp_nal, &pi_nal);
    jbyteArray sps = env->NewByteArray(pp_nal[0].i_payload);
    env->SetByteArrayRegion(sps, 0, pp_nal[0].i_payload, (jbyte *) pp_nal[0].p_payload);
    jbyteArray pps = env->NewByteArray(pp_nal[1].i_payload);
    env->SetByteArrayRegion(pps, 0, pp_nal[1].i_payload, (jbyte *) pp_nal[1].p_payload);

    return env->NewObject(rsCls, rsInit, X264A_OK, sps, pps);
}

/**
 * Free up resources used by the encoder instance.
 * Make sure to call this even if initEncoder fail.
 */
JNIEXPORT void releaseEncoder(JNIEnv *env, jobject thiz) {
    EncoderContext *ctx = (EncoderContext *) get_ctx(env, thiz);

    int nnal;
    x264_nal_t *nal;
    x264_picture_t pic_out;

    if (ctx->encoder != NULL) {
        while (x264_encoder_delayed_frames(ctx->encoder)) {
            x264_encoder_encode(ctx->encoder, &nal, &nnal, NULL, &pic_out);
        }
        x264_encoder_close(ctx->encoder);
        ctx->encoder = NULL;
    }
    free(ctx);
}

/**
 * Encode one frame.
 **/
JNIEXPORT jobject encodeFrame(JNIEnv *env, jobject thiz, jbyteArray frame, jint csp, jlong pts) {
    EncoderContext *ctx = (EncoderContext *) get_ctx(env, thiz);
    jclass rsCls = env->FindClass(X264A_PACKAGE"X264EncodeResult");
    jmethodID rsInit = env->GetMethodID(rsCls, "<init>", "(I[BJZ)V");

    jbyte *input_frame = env->GetByteArrayElements(frame, NULL);

    // encode frame
    int nnal;
    x264_nal_t *nal;
    x264_picture_t out_pic;
    int y_size = ctx->params.i_width * ctx->params.i_height;

    ctx->input_picture.img.i_csp = csp;
    ctx->input_picture.i_pts = pts;
    ctx->input_picture.i_type = X264_TYPE_AUTO;
    ctx->input_picture.img.plane[0] = (uint8_t *) input_frame;
    ctx->input_picture.img.i_stride[0] = ctx->params.i_width;
    switch (csp) {
        case X264_CSP_NV21:
        case X264_CSP_NV12:
            ctx->input_picture.img.i_plane = 2;
            ctx->input_picture.img.plane[1] = ctx->input_picture.img.plane[0] + y_size;
            ctx->input_picture.img.i_stride[1] = ctx->params.i_width;
            break;
        case X264_CSP_I420:
        case X264_CSP_YV12:
            ctx->input_picture.img.i_plane = 3;
            ctx->input_picture.img.plane[1] = ctx->input_picture.img.plane[0] + y_size;
            ctx->input_picture.img.i_stride[1] = ctx->params.i_width / 2;
            ctx->input_picture.img.plane[2] = ctx->input_picture.img.plane[1] + y_size / 4;
            ctx->input_picture.img.i_stride[2] = ctx->params.i_width / 2;
            break;
        default:
            return env->NewObject(rsCls, rsInit, X264A_ERR_NOT_SUPPORT_CPS, NULL, 0, false);
    }

    int len = x264_encoder_encode(ctx->encoder, &nal, &nnal, &ctx->input_picture, &out_pic);
    if (len < 0) return env->NewObject(rsCls, rsInit, X264A_ERR_ENCODE_FRAME, NULL, 0, false);

    // return encoded data
    jbyteArray output_frame = env->NewByteArray(len);
    env->SetByteArrayRegion(output_frame, 0, len, (jbyte *) nal[0].p_payload);

    env->ReleaseByteArrayElements(frame, input_frame, JNI_ABORT);
    return env->NewObject(rsCls, rsInit,
                          X264A_OK, output_frame, out_pic.i_pts, out_pic.i_type == X264_TYPE_IDR);
}

JNIEXPORT jstring getVersion(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("1.3.0");
}

static JNINativeMethod methods[] = {
        {"initEncoder",    "(L" X264A_PACKAGE "X264Params;)L" X264A_PACKAGE "X264InitResult;", (void *) initEncoder},
        {"releaseEncoder", "()V",                                                              (void *) releaseEncoder},
        {"encodeFrame",    "([BIJ)L" X264A_PACKAGE "X264EncodeResult;",                        (void *) encodeFrame},
        {"getVersion",     "()Ljava/lang/String;",                                             (void *) getVersion},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad GetEnv error.");
        return JNI_ERR;
    }

    jclass clz = env->FindClass(X264A_PACKAGE"X264Encoder");
    if (clz == NULL) {
        LOGE("JNI_OnLoad FindClass error.");
        return JNI_ERR;
    }

    if (env->RegisterNatives(clz, methods, sizeof(methods) / sizeof(methods[0]))) {
        LOGE("JNI_OnLoad RegisterNatives error.");
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}