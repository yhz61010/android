#include <jni.h>
#include <string>
#include <android/log.h>
extern "C"
{
#include "libavutil/imgutils.h"
#include "libavformat/avformat.h"
}

#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "adpcm_jni", __VA_ARGS__))

#define GET_ARRAY_LEN(array,len) {len = (sizeof(array) / sizeof(array[0]));}
#define ADPCM_PACKAGE "com/leovp/ffmpeg/adpcm"

AVCodecContext  *ctx    = NULL;

int init(int sampleRate, int channels)
{
  av_log_set_level(AV_LOG_ERROR);
  av_log(NULL, AV_LOG_INFO, "ADPCM init. sampleRate: %d, channels: %d\n", sampleRate, channels);

  const AVCodec *codec = avcodec_find_decoder(AV_CODEC_ID_ADPCM_IMA_QT);
  ctx = avcodec_alloc_context3(codec);
  ctx->sample_rate = sampleRate;
  ctx->channels = channels;
  ctx->channel_layout = av_get_default_channel_layout(ctx->channels);

  int ret = avcodec_open2(ctx, codec, NULL);
  if(ret < 0)
  {
    av_log(NULL, AV_LOG_ERROR, "avcodec_open2 err\n");
    return ret;
  }

  return ret;
}

JNIEXPORT void release(JNIEnv * env, jobject obj)
{
  if(ctx != NULL)
  {
    avcodec_free_context(&ctx);
    ctx = NULL;
  }
}

AVFrame* decode(uint8_t *pic)
{
  int dataLen;
  GET_ARRAY_LEN(pic, dataLen);

  AVPacket *pkt = av_packet_alloc();
  int ret = av_new_packet(pkt, dataLen);
  if(ret < 0)
  {
    av_packet_free(&pkt);
    return NULL;
  }

  pkt->data = pic;
  pkt->pts = dataLen;
  ret = avcodec_send_packet(ctx, pkt);
  if(ret < 0)
  {
    av_packet_free(&pkt);
    return NULL;
  }

  AVFrame *frame = av_frame_alloc();
  ret = avcodec_receive_frame(ctx, frame);
  if (ret < 0)
  {
    av_frame_free(&frame);
    return NULL;
  }
  av_packet_free(&pkt);
  return frame;
}