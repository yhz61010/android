#include "adpcm_ima_qt_decoder.h"
#include "logger.h"

AdpcmImaQtDecoder::AdpcmImaQtDecoder(int sampleRate, int channels) {
    LOGE("ADPCM decoder init. sampleRate: %d, channels: %d", sampleRate, channels);

    this->sampleRate = sampleRate;
    this->channels = channels;

    const AVCodec *codec = avcodec_find_decoder(AV_CODEC_ID_ADPCM_IMA_QT);
    ctx = avcodec_alloc_context3(codec);
    ctx->sample_rate = sampleRate;
    ctx->channels = channels;
    ctx->channel_layout = av_get_default_channel_layout(ctx->channels);

    int ret = avcodec_open2(ctx, codec, nullptr);
    if (ret < 0) {
        LOGE("Decoder: avcodec_open2 error. code=%d", ret);
        exit(0);
    }

    frame = av_frame_alloc();
    pkt = av_packet_alloc();
}

AdpcmImaQtDecoder::~AdpcmImaQtDecoder() {
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
    LOGE("ADPCM decoder released!");
}

uint8_t *AdpcmImaQtDecoder::decode(uint8_t *adpcmByteArray, int adpcmLength, int *outPcmLength) {
    pkt->data = adpcmByteArray;
    pkt->size = adpcmLength;
    int ret;
    if ((ret = avcodec_send_packet(ctx, pkt)) < 0) {
        LOGE("Decoder: avcodec_send_packet() error. code=%d", ret);
        return nullptr;
    }
    if ((ret = avcodec_receive_frame(ctx, frame)) < 0) {
        LOGE("Decoder: avcodec_receive_frame() error. code=%d", ret);
        return nullptr;
    }

    int each_channel_length = frame->linesize[0];
    uint8_t *left_channel_data = frame->data[0];

    int pcmSize = each_channel_length * ctx->channels;
    *outPcmLength = pcmSize;

    if (ctx->channels > 1) { // For stereo
        auto *outPcmBytes = new uint8_t[pcmSize];
        uint8_t *right_channel_data = frame->data[1];
        int subI = 0;
        for (int k = 0; k < pcmSize; k += 4) {
            outPcmBytes[k] = left_channel_data[subI];            // Left channel low 8 bits
            outPcmBytes[k + 1] = left_channel_data[subI + 1];    // Left channel high 8 bits
            outPcmBytes[k + 2] = right_channel_data[subI];       // Right channel low 8 bits
            outPcmBytes[k + 3] = right_channel_data[subI + 1];   // Right channel high 8 bits
            subI += 2;
        }
        return outPcmBytes;
    } else { // For mono
        return left_channel_data;
    }
}

AVCodecContext *AdpcmImaQtDecoder::getCodecContext() {
    return ctx;
}

int AdpcmImaQtDecoder::getSampleRate() const {
    return sampleRate;
}

int AdpcmImaQtDecoder::getChannels() const {
    return channels;
}
