#ifndef LEOANDROIDBASEUTIL_ADPCM_IMA_QT_DECODER_H
#define LEOANDROIDBASEUTIL_ADPCM_IMA_QT_DECODER_H

#include <jni.h>
#include <string>

#ifdef __cplusplus
extern "C" {
#endif

#include <libavcodec/avcodec.h>

#ifdef __cplusplus
}
#endif

class AdpcmImaQtDecoder {
private:
    AVFrame *frame = nullptr;
    AVPacket *pkt = nullptr;
public:
    AVCodecContext *ctx = nullptr;

    int init(int sampleRate, int channels);

    void release();

    uint8_t *decode(uint8_t *adpcmByteArray, int length, int *outPcmLength);

    int chunkSize() const;
};

#endif //LEOANDROIDBASEUTIL_ADPCM_IMA_QT_DECODER_H