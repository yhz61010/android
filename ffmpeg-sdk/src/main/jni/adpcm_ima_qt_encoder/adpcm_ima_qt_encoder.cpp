#include "adpcm_ima_qt_encoder.h"
#include "logger.h"

AdpcmImaQtEncoder::AdpcmImaQtEncoder(int sampleRate, int channels, int bitRate) {
    LOGE("ADPCM encoder init. sampleRate: %d, channels: %d bitRate: %d", sampleRate, channels, bitRate);
    const AVCodec *codec = avcodec_find_encoder(AV_CODEC_ID_ADPCM_IMA_QT);
    if (!codec) {
        LOGE("ADPCM IMA QT encoder does not found");
        exit(-1);
    }
    ctx = avcodec_alloc_context3(codec);
    if (!ctx) {
        LOGE("Could not allocate audio encoder context");
        exit(-2);
    }
    ctx->sample_rate = sampleRate;
    ctx->bit_rate = bitRate;
    ctx->sample_fmt = AV_SAMPLE_FMT_S16P; // ADPCM-IMA-QT only support AV_SAMPLE_FMT_S16P
    ctx->channel_layout = channels == 2 ? AV_CH_LAYOUT_STEREO : AV_CH_LAYOUT_MONO;
    ctx->channels = av_get_channel_layout_nb_channels(ctx->channel_layout);

    int ret;
    /* open it */
    if ((ret = avcodec_open2(ctx, codec, nullptr)) < 0) {
        LOGE("Could not open encoder");
        exit(ret);
    }
    /* packet for holding encoded output */
    pkt = av_packet_alloc();
    if (!pkt) {
        LOGE("Could not allocate the packet");
        exit(-3);
    }
    /* frame containing input raw audio */
    frame = av_frame_alloc();
    if (!frame) {
        LOGE("Could not allocate audio frame");
        exit(-4);
    }

    frame->nb_samples = ctx->frame_size;
    frame->format = ctx->sample_fmt;
    frame->channel_layout = ctx->channel_layout;

    /* allocate the data buffers */
    ret = av_frame_get_buffer(frame, 0);
    if (ret < 0) {
        LOGE("Could not allocate audio data buffers");
        exit(-5);
    }
    ret = av_frame_make_writable(frame);
    if (ret < 0) {
        LOGE("av_frame_make_writable error. code=%d", ret);
        exit(-6);
    }
}

AdpcmImaQtEncoder::~AdpcmImaQtEncoder() {
    if (ctx != nullptr) {
        avcodec_free_context(&ctx);
        ctx = nullptr;
    }
    if (frame != nullptr) {
        av_frame_free(&frame);
        frame = nullptr;
    }
    if (pkt != nullptr) {
        av_packet_free(&pkt);
        pkt = nullptr;
    }
    LOGE("ADPCM encoder released!");
}

void AdpcmImaQtEncoder::encode(const uint8_t *pcm_unit8_t_array, int pcmLen, pCallbackFunc callback) {
    LOGE("channels=%d c->frame_size=%d frame->linesize[0]=%d frame->nb_samples=%d", ctx->channels, ctx->frame_size, frame->linesize[0], frame->nb_samples);

    bool isStereo = ctx->channels == 2;
    uint8_t *outs[ctx->channels];
    const int BUF_SIZE = frame->linesize[0] * ctx->channels;
    outs[0] = new uint8_t[BUF_SIZE];
    if (isStereo)
        outs[1] = new uint8_t[BUF_SIZE];

    const int loopStep = 2 * ctx->channels;
    for (int loop = 0; loop < pcmLen / BUF_SIZE; loop++) {
        for (int idx = 0; idx < BUF_SIZE / loopStep; idx++) {
            outs[0][idx * 2 + 0] = pcm_unit8_t_array[loop * BUF_SIZE + idx * loopStep + 0];
            outs[0][idx * 2 + 1] = pcm_unit8_t_array[loop * BUF_SIZE + idx * loopStep + 1];

            if (isStereo) {
                outs[1][idx * 2 + 0] = pcm_unit8_t_array[loop * BUF_SIZE + idx * loopStep + 2];
                outs[1][idx * 2 + 1] = pcm_unit8_t_array[loop * BUF_SIZE + idx * loopStep + 3];
            }
        }

        frame->data[0] = outs[0];
        if (isStereo)
            frame->data[1] = outs[1];

//        LOGE("Encoder: in loop frame->linesize[0]=%d", frame->linesize[0]);

        do_encode(ctx, frame, pkt, callback);
    }

    delete outs[0];
    if (isStereo)
        delete outs[1];

    /* flush the encoder */
    do_encode(ctx, nullptr, pkt, callback);
}

void AdpcmImaQtEncoder::do_encode(AVCodecContext *pCtx, AVFrame *pFrame, AVPacket *pPkt, pCallbackFunc callback) {
    int ret;

    /* send the frame for encoding */
    ret = avcodec_send_frame(pCtx, pFrame);
    if (ret < 0) {
        LOGE("Error sending the frame to the encoder. code=%d", ret);
        return;
    }

    /* read all the available output packets (in general there may be any
     * number of them */
    while (ret >= 0) {
        ret = avcodec_receive_packet(pCtx, pPkt);
//        LOGE("avcodec_receive_packet ret=%d", ret);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            return;
        else if (ret < 0) {
            LOGE("Error encoding audio frame. code=%d", ret);
            return;
        }

//        LOGE("avcodec_receive_packet pPkt->size=%d", pPkt->size);
        callback(pPkt->data, pPkt->size);

        av_packet_unref(pPkt);
    }
}