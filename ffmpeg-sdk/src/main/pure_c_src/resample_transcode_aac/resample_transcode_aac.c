#include <assert.h>

#include "cmn_util.h"
#include "ffmpeg_util.h"

#include <libavutil/opt.h>
#include <libavutil/timestamp.h>

// gcc -o resample_transcode_aac resample_transcode_aac.c cmn_util.c ffmpeg_util.c -lavutil -lswresample -lavcodec -lavformat -lswscale
// ./resample_transcode_aac output/tingyuanshenshen.mp3 audio.aac
// ffplay -f s16le -ch_layout 3.0 -ar 44100 audio.pcm

static AVFormatContext *in_fmt_ctx = NULL;
static AVCodecContext *in_codec_ctx = NULL;

static AVFormatContext *out_fmt_ctx = NULL;
static AVCodecContext *out_codec_ctx = NULL;

static AVAudioFifo *fifo = NULL;

static SwrContext *swr_ctx = NULL;

static AVPacket *output_packet = NULL;

static int dst_rate;
static AVChannelLayout dst_ch_layout;
static enum AVSampleFormat dst_sample_fmt;

int main(const int argc, char *argv[])
{
    if (argc < 3)
    {
        fprintf(stderr, "Usage: %s <input file> <output file>\n", argv[0]);
        return -1;
    }
    const char *input_file = argv[1];
    const char *output_file = argv[2];

    audio_frame_count = 0;

    int in_audio_stream_idx = -1;
    int ret = open_input_file(input_file, &in_fmt_ctx, &in_codec_ctx, &in_audio_stream_idx);
    if (ret < 0)
        exit(1);
    out_codec_ctx = init_audio_encoder(
        AV_CODEC_ID_AAC,
        44100,
        (AVChannelLayout)AV_CHANNEL_LAYOUT_STEREO,
        AV_SAMPLE_FMT_FLTP,
        128000);

    if (out_codec_ctx == NULL)
        exit(1);
    ret = open_output_file(output_file, out_codec_ctx, &out_fmt_ctx);
    if (ret < 0)
        exit(1);

    /* Initialize the resampler to be able to convert audio sample formats. */
    if (init_resampler(in_codec_ctx, out_codec_ctx, &swr_ctx))
        goto cleanup;

    /* Initialize the FIFO buffer to store audio samples to be encoded. */
    if (init_fifo(&fifo, out_codec_ctx))
        goto cleanup;

    ret = write_output_file_header(out_fmt_ctx);
    if (ret < 0)
        exit(1);

    const AVChannelLayout src_ch_layout = in_codec_ctx->ch_layout;
    dst_ch_layout = out_codec_ctx->ch_layout;
    const int src_rate = in_codec_ctx->sample_rate;
    dst_rate = out_codec_ctx->sample_rate;
    const enum AVSampleFormat src_sample_fmt = in_codec_ctx->sample_fmt;
    // int src_nb_channels = src_ch_layout.nb_channels, dst_nb_channels = dst_ch_layout.nb_channels;
    // int src_linesize, dst_linesize;
    // int src_nb_samples = in_codec_ctx->frame_size, dst_nb_samples, max_dst_nb_samples;
    dst_sample_fmt = out_codec_ctx->sample_fmt;

    const enum AVSampleFormat src_packed_sfmt = get_packed_sample_fmt(src_sample_fmt);
    const char *src_format_from_packed_sfmt = get_format_from_sample_fmt(src_packed_sfmt);
    if (NULL == src_format_from_packed_sfmt)
    {
        fprintf(stderr, "Unsupported sample format for source: %d\n", src_packed_sfmt);
        exit(2);
    }

    const enum AVSampleFormat dst_packed_sfmt = get_packed_sample_fmt(dst_sample_fmt);
    const char *dst_format_from_packed_sfmt = get_format_from_sample_fmt(dst_packed_sfmt);
    if (NULL == dst_format_from_packed_sfmt)
    {
        fprintf(stderr, "Unsupported sample format for target: %d\n", dst_packed_sfmt);
        exit(3);
    }

    char src_ch_name_buf[64];
    av_channel_layout_describe(&src_ch_layout, src_ch_name_buf, sizeof(src_ch_name_buf));
    fprintf(stderr, "=====> source: %dHz %s %s(%d) %s(%d)\n", src_rate, src_ch_name_buf, src_format_from_packed_sfmt,
            src_packed_sfmt, av_get_sample_fmt_name(src_sample_fmt), src_sample_fmt);

    char dst_ch_name_buf[64];
    av_channel_layout_describe(&dst_ch_layout, dst_ch_name_buf, sizeof(dst_ch_name_buf));
    fprintf(stderr, "=====> target: %dHz %s %s(%d) %s(%d)\n", dst_rate, dst_ch_name_buf, dst_format_from_packed_sfmt,
            dst_packed_sfmt, av_get_sample_fmt_name(dst_sample_fmt), dst_sample_fmt);

    char debug_input_file_name_buffer[256];
    sprintf(debug_input_file_name_buffer, "/tmp/input_pcm.%s", src_format_from_packed_sfmt);
    fprintf(stderr, "debug_input_file_name_buffer=%s\n", debug_input_file_name_buffer);
    input_audio_file = fopen(debug_input_file_name_buffer, "wb");

    char debug_output_file_name_buffer[256];
    sprintf(debug_output_file_name_buffer, "/tmp/output_pcm.%s", dst_format_from_packed_sfmt);
    fprintf(stderr, "debug_output_file_name_buffer=%s\n", debug_output_file_name_buffer);
    output_audio_file = fopen(debug_output_file_name_buffer, "wb");
    if (output_audio_file == NULL)
    {
        fprintf(stderr, "Could not open debug destination file.\n");
        exit(9999);
    }

    /* Loop as long as we have input samples to read or output samples
     * to write; abort as soon as we have neither. */
    while (1)
    {
        /* Use the encoder's desired frame size for processing. */
        const int output_frame_size = out_codec_ctx->frame_size;
        int finished = 0;

        /* Make sure that there is one frame worth of samples in the FIFO
         * buffer so that the encoder can do its work.
         * Since the decoder's and the encoder's frame size may differ, we
         * need to FIFO buffer to store as many frames worth of input samples
         * that they make up at least one frame worth of output samples. */
        while (av_audio_fifo_size(fifo) < output_frame_size)
        {
            /* Decode one frame worth of audio samples, convert it to the
             * output sample format and put it into the FIFO buffer. */
            if (read_decode_convert_and_store(in_audio_stream_idx,
                                              fifo,
                                              in_fmt_ctx,
                                              in_codec_ctx,
                                              out_codec_ctx,
                                              swr_ctx, &finished))
                goto cleanup;

            /* If we are at the end of the input file, we continue
             * encoding the remaining audio samples to the output file. */
            if (finished)
                break;
        }

        /* If we have enough samples for the encoder, we encode them.
         * At the end of the file, we pass the remaining samples to
         * the encoder. */
        while (av_audio_fifo_size(fifo) >= output_frame_size ||
               (finished && av_audio_fifo_size(fifo) > 0))
            /* Take one frame worth of audio samples from the FIFO buffer,
             * encode it and write it to the output file. */
            if (load_encode_and_write(fifo, out_fmt_ctx,
                                      out_codec_ctx))
                goto cleanup;

        /* If we are at the end of the input file and have encoded
         * all remaining samples, we can exit this loop and finish. */
        if (finished)
        {
            int data_written;
            /* Flush the encoder as it may have delayed frames. */
            do
            {
                if (encode_audio_frame(NULL, out_fmt_ctx,
                                       out_codec_ctx, &data_written))
                    goto cleanup;
            } while (data_written);
            break;
        }
    } // end of while

    /* Write the trailer of the output file container. */
    if (write_output_file_trailer(out_fmt_ctx) < 0)
        goto cleanup;

    fprintf(stderr, "=====> source: %dHz %s %s(%d) %s(%d)\n", src_rate, src_ch_name_buf,
            src_format_from_packed_sfmt, src_packed_sfmt,
            av_get_sample_fmt_name(src_sample_fmt), src_sample_fmt);
    fprintf(stderr, "=====> target: %dHz %s %s(%d) %s(%d)\n", dst_rate, dst_ch_name_buf,
            dst_format_from_packed_sfmt, dst_packed_sfmt,
            av_get_sample_fmt_name(dst_sample_fmt), dst_sample_fmt);

    printf("Play the resample pcm audio file with the command:\n"
           // "$ ffmpeg -f %s -ac %d -ar %d -i %s -f wav - | ffplay -\n",
           "$ ffplay -f %s -ch_layout %s -ar %d %s\n",
           dst_format_from_packed_sfmt, dst_ch_name_buf, dst_rate, debug_output_file_name_buffer);

cleanup:
    // Cleanup
    if (fifo)
        av_audio_fifo_free(fifo);
    if (input_audio_file)
        fclose(input_audio_file);
    if (output_audio_file)
        fclose(output_audio_file);
    // av_frame_free(&input_frame);
    // av_frame_free(&resampled_frame);
    // av_packet_free(&input_packet);
    if (output_packet != NULL)
        av_packet_free(&output_packet);

    swr_free(&swr_ctx);
    avcodec_free_context(&in_codec_ctx);
    avcodec_free_context(&out_codec_ctx);
    avformat_close_input(&in_fmt_ctx);

    if (out_fmt_ctx && !(out_fmt_ctx->oformat->flags & AVFMT_NOFILE))
        avio_closep(&out_fmt_ctx->pb);
    avformat_free_context(out_fmt_ctx);

    return ret;
}
