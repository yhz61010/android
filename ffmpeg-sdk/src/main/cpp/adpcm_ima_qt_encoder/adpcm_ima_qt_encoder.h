#ifndef LEOANDROIDBASEUTIL_ADPCM_IMA_QT_ENCODER_H
#define LEOANDROIDBASEUTIL_ADPCM_IMA_QT_ENCODER_H

#include <jni.h>
#include <string>

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

typedef void(*pCallbackFunc)(uint8_t *encodedAudioData, int decodedAudioLength);

class AdpcmImaQtEncoder {
private:
    AVCodecContext *ctx = nullptr;
    AVFrame *frame = nullptr;
    AVPacket *pkt = nullptr;

    static void do_encode(AVCodecContext *pCtx, AVFrame *pFrame, AVPacket *pPkt, pCallbackFunc callback);

public:
    AdpcmImaQtEncoder(int sampleRate, int channels, int bitRate);
    ~AdpcmImaQtEncoder();

    void encode(const uint8_t *pcmByteArray, int pcmLen, pCallbackFunc callback);
};

#endif //LEOANDROIDBASEUTIL_ADPCM_IMA_QT_ENCODER_H