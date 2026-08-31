#include "adpcm_ima_qt_decoder.h"
#include "logger.h"

#include <cstring>
#include <limits>
#include <new>

AdpcmImaQtDecoder::AdpcmImaQtDecoder(int sampleRate, int channels) {
    LOGE("ADPCM decoder init. sampleRate: %d, channels: %d", sampleRate, channels);

    this->sampleRate = sampleRate;
    this->channels = channels;

    const AVCodec *codec = avcodec_find_decoder(AV_CODEC_ID_ADPCM_IMA_QT);
    if (!codec) {
        LOGE("Decoder: ADPCM IMA QT decoder not found");
        return;
    }
    ctx = avcodec_alloc_context3(codec);
    if (!ctx) {
        LOGE("Decoder: Could not allocate codec context");
        return;
    }
    ctx->sample_rate = sampleRate;
    ctx->ch_layout = channels == 2 ? (AVChannelLayout) AV_CHANNEL_LAYOUT_STEREO : (AVChannelLayout) AV_CHANNEL_LAYOUT_MONO;

    int ret = avcodec_open2(ctx, codec, nullptr);
    if (ret < 0) {
        LOGE("Decoder: avcodec_open2 error. code=%d", ret);
        avcodec_free_context(&ctx);
        ctx = nullptr;
        return;
    }

    frame = av_frame_alloc();
    pkt = av_packet_alloc();
    if (frame == nullptr || pkt == nullptr) {
        LOGE("Decoder: Could not allocate frame or packet");
        if (frame != nullptr) av_frame_free(&frame);
        if (pkt != nullptr) av_packet_free(&pkt);
        avcodec_free_context(&ctx);
        return;
    }
    valid = true;
}

AdpcmImaQtDecoder::~AdpcmImaQtDecoder() {
    if (frame != nullptr) {
        av_frame_free(&frame);
        frame = nullptr;
    }
    if (pkt != nullptr) {
        av_packet_free(&pkt);
        pkt = nullptr;
    }
    if (ctx != nullptr) {
        avcodec_free_context(&ctx);
        ctx = nullptr;
    }
    LOGE("ADPCM decoder released!");
}

uint8_t *AdpcmImaQtDecoder::decode(const uint8_t *adpcmByteArray, int adpcmLength,
                                   int *outPcmLength) {
    *outPcmLength = 0;
    av_packet_unref(pkt);
    av_frame_unref(frame);
    int ret = av_new_packet(pkt, adpcmLength);
    if (ret < 0) {
        LOGE("Decoder: av_new_packet() error. code=%d", ret);
        return nullptr;
    }
    memcpy(pkt->data, adpcmByteArray, adpcmLength);
    memset(pkt->data + adpcmLength, 0, AV_INPUT_BUFFER_PADDING_SIZE);
    if ((ret = avcodec_send_packet(ctx, pkt)) < 0) {
        LOGE("Decoder: avcodec_send_packet() error. code=%d", ret);
        av_packet_unref(pkt);
        return nullptr;
    }
    if ((ret = avcodec_receive_frame(ctx, frame)) < 0) {
        LOGE("Decoder: avcodec_receive_frame() error. code=%d", ret);
        av_packet_unref(pkt);
        return nullptr;
    }

    const int bytes_per_sample = av_get_bytes_per_sample((AVSampleFormat) frame->format);
    const int channel_count = frame->ch_layout.nb_channels;
    const int64_t each_channel_length =
            static_cast<int64_t>(frame->nb_samples) * bytes_per_sample;
    const int64_t pcm_size = each_channel_length * channel_count;
    if (bytes_per_sample <= 0 || frame->nb_samples <= 0 ||
        channel_count != channels || each_channel_length > std::numeric_limits<int>::max() ||
        pcm_size <= 0 || pcm_size > std::numeric_limits<int>::max()) {
        av_packet_unref(pkt);
        av_frame_unref(frame);
        return nullptr;
    }
    const int pcmSize = static_cast<int>(pcm_size);
    *outPcmLength = pcmSize;

    auto *outPcmBytes = new(std::nothrow) uint8_t[pcmSize];
    if (outPcmBytes == nullptr) {
        av_packet_unref(pkt);
        av_frame_unref(frame);
        *outPcmLength = 0;
        return nullptr;
    }
    if (av_sample_fmt_is_planar((AVSampleFormat) frame->format) && channel_count > 1) {
        if (frame->extended_data == nullptr) {
            delete[] outPcmBytes;
            av_packet_unref(pkt);
            av_frame_unref(frame);
            *outPcmLength = 0;
            return nullptr;
        }
        for (int channel = 0; channel < channel_count; ++channel) {
            if (frame->extended_data[channel] == nullptr) {
                delete[] outPcmBytes;
                av_packet_unref(pkt);
                av_frame_unref(frame);
                *outPcmLength = 0;
                return nullptr;
            }
        }
        for (int sample = 0; sample < frame->nb_samples; ++sample) {
            for (int channel = 0; channel < channel_count; ++channel) {
                memcpy(outPcmBytes + (sample * channel_count + channel) * bytes_per_sample,
                       frame->extended_data[channel] + sample * bytes_per_sample,
                       bytes_per_sample);
            }
        }
    } else {
        if (frame->data[0] == nullptr) {
            delete[] outPcmBytes;
            av_packet_unref(pkt);
            av_frame_unref(frame);
            *outPcmLength = 0;
            return nullptr;
        }
        memcpy(outPcmBytes, frame->data[0], pcmSize);
    }
    av_packet_unref(pkt);
    av_frame_unref(frame);
    return outPcmBytes;
}

AVCodecContext *AdpcmImaQtDecoder::getCodecContext() {
    return ctx;
}

[[maybe_unused]] int AdpcmImaQtDecoder::getSampleRate() const {
    return sampleRate;
}

int AdpcmImaQtDecoder::getChannels() const {
    return channels;
}
