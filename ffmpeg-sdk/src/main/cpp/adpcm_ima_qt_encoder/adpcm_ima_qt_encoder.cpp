#include "adpcm_ima_qt_encoder.h"
#include "logger.h"

AdpcmImaQtEncoder::AdpcmImaQtEncoder(int sampleRate, int channels, int bitRate) {
    LOGE("ADPCM encoder init. sampleRate: %d, channels: %d bitRate: %d", sampleRate, channels, bitRate);
    const AVCodec *codec = avcodec_find_encoder(AV_CODEC_ID_ADPCM_IMA_QT);
    if (!codec) {
        LOGE("ADPCM IMA QT encoder does not found");
        return;
    }
    ctx = avcodec_alloc_context3(codec);
    if (!ctx) {
        LOGE("Could not allocate audio encoder context");
        return;
    }
    ctx->sample_rate = sampleRate;
    ctx->bit_rate = bitRate;
    ctx->sample_fmt = AV_SAMPLE_FMT_S16P; // ADPCM-IMA-QT only support AV_SAMPLE_FMT_S16P
    ctx->ch_layout = channels == 2 ? (AVChannelLayout) AV_CHANNEL_LAYOUT_STEREO : (AVChannelLayout) AV_CHANNEL_LAYOUT_MONO;

    int ret;
    if ((ret = avcodec_open2(ctx, codec, nullptr)) < 0) {
        LOGE("Could not open encoder. code=%d", ret);
        avcodec_free_context(&ctx);
        ctx = nullptr;
        return;
    }
    pkt = av_packet_alloc();
    if (!pkt) {
        LOGE("Could not allocate the packet");
        avcodec_free_context(&ctx);
        ctx = nullptr;
        return;
    }
    frame = av_frame_alloc();
    if (!frame) {
        LOGE("Could not allocate audio frame");
        av_packet_free(&pkt);
        pkt = nullptr;
        avcodec_free_context(&ctx);
        ctx = nullptr;
        return;
    }

    frame->nb_samples = ctx->frame_size;
    frame->format = ctx->sample_fmt;
    frame->ch_layout = ctx->ch_layout;

    ret = av_frame_get_buffer(frame, 0);
    if (ret < 0) {
        LOGE("Could not allocate audio data buffers. code=%d", ret);
        av_frame_free(&frame);
        frame = nullptr;
        av_packet_free(&pkt);
        pkt = nullptr;
        avcodec_free_context(&ctx);
        ctx = nullptr;
        return;
    }

    LOGE("frame_size=%d linesize[0]=%d nb_samples=%d", ctx->frame_size, frame->linesize[0], frame->nb_samples);
    valid = true;
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

void AdpcmImaQtEncoder::encode(const uint8_t *pcm_unit8_t_array, int pcmLen, const EncoderCallback &callback) {
    bool isStereo = ctx->ch_layout.nb_channels == 2;
    const int BUF_SIZE = frame->linesize[0] * ctx->ch_layout.nb_channels;
    auto *out0 = new uint8_t[BUF_SIZE];
    uint8_t *out1 = nullptr;
    if (isStereo) out1 = new uint8_t[BUF_SIZE];

    const int loopStep = 2 * ctx->ch_layout.nb_channels;
    int ret;
    for (int loop = 0; loop < pcmLen / BUF_SIZE; loop++) {
        ret = av_frame_make_writable(frame);
        if (ret < 0) {
            LOGE("av_frame_make_writable error. code=%d", ret);
            break;
        }

        for (int idx = 0; idx < BUF_SIZE / loopStep; idx++) {
            out0[idx * 2 + 0] = pcm_unit8_t_array[loop * BUF_SIZE + idx * loopStep + 0];
            out0[idx * 2 + 1] = pcm_unit8_t_array[loop * BUF_SIZE + idx * loopStep + 1];

            if (isStereo) {
                out1[idx * 2 + 0] = pcm_unit8_t_array[loop * BUF_SIZE + idx * loopStep + 2];
                out1[idx * 2 + 1] = pcm_unit8_t_array[loop * BUF_SIZE + idx * loopStep + 3];
            }
        }

        frame->data[0] = out0;
        if (isStereo) frame->data[1] = out1;

        do_encode(ctx, frame, pkt, callback);
    }

    delete[] out0;
    delete[] out1; // safe to delete[] nullptr
}

void AdpcmImaQtEncoder::do_encode(AVCodecContext *pCtx, AVFrame *pFrame, AVPacket *pPkt, const EncoderCallback &callback) {
    int ret = avcodec_send_frame(pCtx, pFrame);
    if (ret < 0) {
        LOGE("Error sending the frame to the encoder. code=%d", ret);
        return;
    }

    for(;;) {
        ret = avcodec_receive_packet(pCtx, pPkt);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            return;
        else if (ret < 0) {
            LOGE("Error encoding audio frame. code=%d", ret);
            return;
        }

        callback(pPkt->data, pPkt->size);
        av_packet_unref(pPkt);
    }
}
