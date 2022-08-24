#ifndef LEOANDROIDBASEUTIL_ADPCM_IMA_QT_DECODER_H
#define LEOANDROIDBASEUTIL_ADPCM_IMA_QT_DECODER_H

#include <jni.h>
#include <string>

#ifdef __cplusplus
extern "C" {
#endif

#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>

#ifdef __cplusplus
}
#endif

class AdpcmImaQtDecoder {
private:
    AVFrame *frame = nullptr;
    AVPacket *pkt = nullptr;
    AVCodecContext *ctx = nullptr;

    int sampleRate;
    int channels;
public:
    AdpcmImaQtDecoder(int sampleRate, int channels);

    ~AdpcmImaQtDecoder();

    uint8_t *decode(uint8_t *adpcmByteArray, int length, int *outPcmLength);

    int getSampleRate() const;

    int getChannels() const;

    AVCodecContext *getCodecContext();
};

#endif //LEOANDROIDBASEUTIL_ADPCM_IMA_QT_DECODER_H
