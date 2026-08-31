#include "adpcm_ima_qt_encoder.h"
#include "logger.h"

#include <cerrno>
#include <climits>
#include <cstring>

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

int AdpcmImaQtEncoder::encode(const uint8_t *pcm_unit8_t_array, int pcmLen,
                              const EncoderCallback &callback) {
    const int bytes_per_sample = av_get_bytes_per_sample(ctx->sample_fmt);
    const int channels = ctx->ch_layout.nb_channels;
    const int frame_bytes = getInputFrameBytes();
    if (pcm_unit8_t_array == nullptr || pcmLen <= 0 || frame_bytes <= 0 ||
        pcmLen % frame_bytes != 0) {
        return AVERROR(EINVAL);
    }

    for (int loop = 0; loop < pcmLen / frame_bytes; loop++) {
        int ret = av_frame_make_writable(frame);
        if (ret < 0) {
            LOGE("av_frame_make_writable error. code=%d", ret);
            return ret;
        }

        const uint8_t *input_frame = pcm_unit8_t_array + loop * frame_bytes;
        for (int sample = 0; sample < frame->nb_samples; ++sample) {
            for (int channel = 0; channel < channels; ++channel) {
                memcpy(frame->data[channel] + sample * bytes_per_sample,
                       input_frame + (sample * channels + channel) * bytes_per_sample,
                       bytes_per_sample);
            }
        }

        ret = do_encode(ctx, frame, pkt, callback);
        if (ret < 0) return ret;
    }
    return 0;
}

int AdpcmImaQtEncoder::getInputFrameBytes() const {
    if (ctx == nullptr || frame == nullptr) return 0;
    const int bytes_per_sample = av_get_bytes_per_sample(ctx->sample_fmt);
    const int channels = ctx->ch_layout.nb_channels;
    const int64_t frame_bytes =
            static_cast<int64_t>(frame->nb_samples) * channels * bytes_per_sample;
    return bytes_per_sample > 0 && channels > 0 && frame_bytes <= INT_MAX
           ? static_cast<int>(frame_bytes)
           : 0;
}

int AdpcmImaQtEncoder::do_encode(AVCodecContext *pCtx, AVFrame *pFrame, AVPacket *pPkt,
                                 const EncoderCallback &callback) {
    av_packet_unref(pPkt);
    int ret = avcodec_send_frame(pCtx, pFrame);
    if (ret < 0) {
        LOGE("Error sending the frame to the encoder. code=%d", ret);
        return ret;
    }

    for(;;) {
        ret = avcodec_receive_packet(pCtx, pPkt);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
            return 0;
        else if (ret < 0) {
            LOGE("Error encoding audio frame. code=%d", ret);
            av_packet_unref(pPkt);
            return ret;
        }

        const bool should_continue = callback(pPkt->data, pPkt->size);
        av_packet_unref(pPkt);
        if (!should_continue) return AVERROR_EXIT;
    }
}
