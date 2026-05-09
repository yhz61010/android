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
    bool valid = false;
public:
    AdpcmImaQtDecoder(int sampleRate, int channels);

    ~AdpcmImaQtDecoder();

    [[nodiscard]] bool isValid() const { return valid; }

    /**
     * Decodes ADPCM data to PCM. The returned buffer is always newly allocated
     * and must be freed by the caller with delete[].
     */
    uint8_t *decode(uint8_t *adpcmByteArray, int length, int *outPcmLength);

    [[maybe_unused]] [[nodiscard]] int getSampleRate() const;

    [[nodiscard]] int getChannels() const;

    AVCodecContext *getCodecContext();
};

#endif //LEOANDROIDBASEUTIL_ADPCM_IMA_QT_DECODER_H
