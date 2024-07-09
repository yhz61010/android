#ifndef FFMPEG_UTIL_H
#define FFMPEG_UTIL_H

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswresample/swresample.h>
#include <libavutil/audio_fifo.h>

FILE *input_audio_file;
FILE *output_audio_file;
int audio_frame_count;

/**
 * Initialize one data packet for reading or writing.
 * @param[out] packet Packet to be initialized
 * @return Error code (0 if successful)
 */
int init_packet(AVPacket **packet);

/**
 * Initialize one audio frame for reading from the input file.
 * @param[out] frame Frame to be initialized
 * @return Error code (0 if successful)
 */
int init_input_frame(AVFrame **frame);

/**
 * Open an input file and the required decoder.
 *
 * @param filename File to be opened
 * @param[out] input_fmt_ctx Format context of opened file
 * @param[out] input_codec_ctx Codec context of opened file
 * @param[out] input_audio_stream_idx The audio stream index
 *
 * @return  >= 0 in case of success, a negative AVERROR code in case of failure
 */
int open_input_file(const char *filename,
                    AVFormatContext **input_fmt_ctx,
                    AVCodecContext **input_codec_ctx,
                    int *input_audio_stream_idx);

/**
 * Open an output file and the required encoder.
 * Also set some basic encoder parameters.
 * Some of these parameters are based on the input file's parameters.
 *
 * @param filename File to be opened
 * @param[in] input_codec_ctx Codec context of input file
 * @param[out] output_fmt_ctx Codec context of output file
 * @return >= 0 in case of success, a negative AVERROR code in case of failure
 */
int open_output_file(const char *filename,
                     AVCodecContext *input_codec_ctx,
                     AVFormatContext **output_fmt_ctx);

/**
 *
 * @param[out] stream_idx
 * @param[out] dec_ctx
 * @param[in] fmt_ctx
 * @param type
 * @return
 */
int open_codec_context(int *stream_idx,
                       AVCodecContext **dec_ctx,
                       AVFormatContext *fmt_ctx,
                       const enum AVMediaType type);

/**
 *
 * @param av_codec_id
 * @param dst_sample_rate
 * @param dst_ch_layout
 * @param dst_sample_format If this value is AV_SAMPLE_FMT_NONE, it will use the first supported format the specific encoder.
 * @param bit_rate
 *
 * @return The AVCodecContext pointer of audio encoder or null if failed.
 */
AVCodecContext *init_audio_encoder(const enum AVCodecID av_codec_id,
                                   const int dst_sample_rate,
                                   const AVChannelLayout dst_ch_layout,
                                   const enum AVSampleFormat dst_sample_format,
                                   const int64_t bit_rate);

/**
 * Initialize the audio resampler based on the input and output codec settings.
 * If the input and output sample formats differ, a conversion is required
 * libswresample takes care of this, but requires initialization.
 * @param      input_codec_context  Codec context of the input file
 * @param      output_codec_context Codec context of the output file
 * @param[out] resample_context     Resample context for the required conversion
 * @return Error code (0 if successful)
 */
int init_resampler(AVCodecContext *input_codec_context,
                   AVCodecContext *output_codec_context,
                   SwrContext **resample_context);

/**
 * Initialize a FIFO buffer for the audio samples to be encoded.
 * @param[out] fifo                 Sample buffer
 * @param      output_codec_context Codec context of the output file
 * @return Error code (0 if successful)
 */
int init_fifo(AVAudioFifo **fifo, AVCodecContext *output_codec_context);

/**
 * Write the header of the output file container.
 * @param output_format_context Format context of the output file
 * @return Error code (0 if successful)
 */
int write_output_file_header(AVFormatContext *output_format_context);

/**
 * Decode one audio frame from the input file.
 * @param      frame                Audio frame to be decoded
 * @param      input_format_context Format context of the input file
 * @param      input_codec_context  Codec context of the input file
 * @param[out] data_present         Indicates whether data has been decoded
 * @param[out] finished             Indicates whether the end of file has
 *                                  been reached and all data has been
 *                                  decoded. If this flag is false, there
 *                                  is more data to be decoded, i.e., this
 *                                  function has to be called again.
 * @return Error code (0 if successful)
 */
int decode_audio_frame(int in_audio_stream_idx,
                       AVFrame *frame,
                       AVFormatContext *input_format_context,
                       AVCodecContext *input_codec_context,
                       int *data_present, int *finished);

/**
 * Initialize a temporary storage for the specified number of audio samples.
 * The conversion requires temporary storage due to the different format.
 * The number of audio samples to be allocated is specified in frame_size.
 * @param[out] converted_input_samples Array of converted samples. The
 *                                     dimensions are reference, channel
 *                                     (for multi-channel audio), sample.
 * @param      output_codec_context    Codec context of the output file
 * @param      frame_size              Number of samples to be converted in
 *                                     each round
 * @return Error code (0 if successful)
 */
int init_converted_samples(uint8_t ***converted_input_samples,
                           AVCodecContext *output_codec_context,
                           int frame_size);

/**
 * Convert the input audio samples into the output sample format.
 * The conversion happens on a per-frame basis, the size of which is
 * specified by frame_size.
 * @param      input_data       Samples to be decoded. The dimensions are
 *                              channel (for multi-channel audio), sample.
 * @param[out] converted_data   Converted samples. The dimensions are channel
 *                              (for multi-channel audio), sample.
 * @param      frame_size       Number of samples to be converted
 * @param      resample_context Resample context for the conversion
 * @return Number of samples output per channel or error code
 */
int convert_samples(const uint8_t **input_data,
                    uint8_t **converted_data, const int frame_size,
                    SwrContext *resample_context);

/**
 * Add converted input audio samples to the FIFO buffer for later processing.
 * @param fifo                    Buffer to add the samples to
 * @param converted_input_samples Samples to be added. The dimensions are channel
 *                                (for multi-channel audio), sample.
 * @param frame_size              Number of samples to be converted
 * @return Error code (0 if successful)
 */
int add_samples_to_fifo(AVAudioFifo *fifo,
                        uint8_t **converted_input_samples,
                        const int frame_size);

/**
 * Read one audio frame from the input file, decode, convert and store
 * it in the FIFO buffer.
 * @param      fifo                 Buffer used for temporary storage
 * @param      input_format_context Format context of the input file
 * @param      input_codec_context  Codec context of the input file
 * @param      output_codec_context Codec context of the output file
 * @param      resampler_context    Resample context for the conversion
 * @param[out] finished             Indicates whether the end of file has
 *                                  been reached and all data has been
 *                                  decoded. If this flag is false,
 *                                  there is more data to be decoded,
 *                                  i.e., this function has to be called
 *                                  again.
 * @return Error code (0 if successful)
 */
int read_decode_convert_and_store(int in_audio_stream_idx,
                                  AVAudioFifo *fifo,
                                  AVFormatContext *input_format_context,
                                  AVCodecContext *input_codec_context,
                                  AVCodecContext *output_codec_context,
                                  SwrContext *resampler_context,
                                  int *finished);

/**
 * Initialize one input frame for writing to the output file.
 * The frame will be exactly frame_size samples large.
 * @param[out] frame                Frame to be initialized
 * @param      output_codec_context Codec context of the output file
 * @param      frame_size           Size of the frame
 * @return Error code (0 if successful)
 */
int init_output_frame(AVFrame **frame,
                      AVCodecContext *output_codec_context,
                      int frame_size);

/**
 * Encode one frame worth of audio to the output file.
 * @param      frame                 Samples to be encoded
 * @param      output_format_context Format context of the output file
 * @param      output_codec_context  Codec context of the output file
 * @param[out] data_present          Indicates whether data has been
 *                                   encoded
 * @return Error code (0 if successful)
 */
int encode_audio_frame(AVFrame *frame,
                       AVFormatContext *output_format_context,
                       AVCodecContext *output_codec_context,
                       int *data_present);

/**
 * Load one audio frame from the FIFO buffer, encode and write it to the
 * output file.
 * @param fifo                  Buffer used for temporary storage
 * @param output_format_context Format context of the output file
 * @param output_codec_context  Codec context of the output file
 * @return Error code (0 if successful)
 */
int load_encode_and_write(AVAudioFifo *fifo,
                          AVFormatContext *output_format_context,
                          AVCodecContext *output_codec_context);

/**
 * Write the trailer of the output file container.
 * @param output_format_context Format context of the output file
 * @return Error code (0 if successful)
 */
int write_output_file_trailer(AVFormatContext *output_format_context);

#endif // FFMPEG_UTIL_H
