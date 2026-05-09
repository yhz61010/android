#ifndef LEOANDROIDBASEUTIL_ADPCM_IMA_QT_ENCODER_H
#define LEOANDROIDBASEUTIL_ADPCM_IMA_QT_ENCODER_H

#include <jni.h>
#include <string>
#include <functional>

#ifdef __cplusplus
extern "C" {
#endif

#include <libavcodec/avcodec.h>

#include <libavutil/channel_layout.h>
#include <libavutil/common.h>
#include <libavutil/frame.h>
#include <libavutil/samplefmt.h>

#ifdef __cplusplus
}
#endif

using EncoderCallback = std::function<void(uint8_t *encodedAudioData, int encodedAudioLength)>;

class AdpcmImaQtEncoder {
private:
    AVCodecContext *ctx = nullptr;
    AVFrame *frame = nullptr;
    AVPacket *pkt = nullptr;
    bool valid = false;

    static void do_encode(AVCodecContext *pCtx, AVFrame *pFrame, AVPacket *pPkt, const EncoderCallback &callback);

public:
    AdpcmImaQtEncoder(int sampleRate, int channels, int bitRate);
    ~AdpcmImaQtEncoder();

    [[nodiscard]] bool isValid() const { return valid; }

    void encode(const uint8_t *pcmByteArray, int pcmLen, const EncoderCallback &callback);
};

#endif //LEOANDROIDBASEUTIL_ADPCM_IMA_QT_ENCODER_H
