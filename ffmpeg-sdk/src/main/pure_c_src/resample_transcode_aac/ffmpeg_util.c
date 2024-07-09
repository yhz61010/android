#include "ffmpeg_util.h"
#include <libavutil/timestamp.h>
// #include <libavutil/avassert.h>

/* Global timestamp for the audio frames. */
static int64_t pts = 0;

int init_packet(AVPacket **packet)
{
    if (!(*packet = av_packet_alloc()))
    {
        fprintf(stderr, "Could not allocate packet\n");
        return AVERROR(ENOMEM);
    }
    return 0;
}

int init_input_frame(AVFrame **frame)
{
    if (!(*frame = av_frame_alloc()))
    {
        fprintf(stderr, "Could not allocate input frame\n");
        return AVERROR(ENOMEM);
    }
    return 0;
}

int open_codec_context(int *stream_idx,
                       AVCodecContext **dec_ctx,
                       AVFormatContext *fmt_ctx,
                       const enum AVMediaType type)
{
    int ret;
    const AVCodec *dec = NULL;

    const int stream_index = av_find_best_stream(fmt_ctx, type, -1, -1, NULL, 0);
    if (stream_index < 0)
    {
        fprintf(stderr, "Could not find audio stream in input file\n");
        return stream_index;
    }
    *stream_idx = stream_index;
    const AVStream *st = fmt_ctx->streams[stream_index];
    fprintf(stderr, "----> input audio stream_idx=%d  time_base.den=%d  time_base.num=%d\n", *stream_idx, st->time_base.den, st->time_base.num);

    /* Find a decoder for the audio stream. */
    dec = avcodec_find_decoder(st->codecpar->codec_id);
    if (dec == NULL)
    {
        fprintf(stderr, "Failed to find %s codec\n", av_get_media_type_string(type));
        avformat_close_input(&fmt_ctx);
        return AVERROR(EINVAL);
    }

    /* Allocate a codec context for the decoder */
    *dec_ctx = avcodec_alloc_context3(dec);
    if (*dec_ctx == NULL)
    {
        fprintf(stderr, "Failed to allocate the %s codec context\n",
                av_get_media_type_string(type));
        avformat_close_input(&fmt_ctx);
        return AVERROR(ENOMEM);
    }

    /* Copy codec parameters from input stream to output codec context */
    if ((ret = avcodec_parameters_to_context(*dec_ctx, st->codecpar)) < 0)
    {
        fprintf(stderr, "Failed to copy %s codec parameters to decoder context\n",
                av_get_media_type_string(type));
        avformat_close_input(&fmt_ctx);
        avcodec_free_context(dec_ctx);
        return ret;
    }

    /* Open the decoder for the audio stream to use it later. */
    if ((ret = avcodec_open2(*dec_ctx, dec, NULL)) < 0)
    {
        fprintf(stderr, "Failed to open %s codec\n", av_get_media_type_string(type));
        avcodec_free_context(dec_ctx);
        avformat_close_input(&fmt_ctx);
        return ret;
    }

    /* Set the packet timebase for the decoder. */
    (*dec_ctx)->pkt_timebase = st->time_base;

    return 0;
}

AVCodecContext *init_audio_encoder(const enum AVCodecID av_codec_id,
                                   const int dst_sample_rate,
                                   const AVChannelLayout dst_ch_layout,
                                   const enum AVSampleFormat dst_sample_format,
                                   const int64_t bit_rate)
{
    /* Find the encoder to be used by its name. */
    const AVCodec *encoder = avcodec_find_encoder(av_codec_id);
    if (!encoder)
    {
        fprintf(stderr, "Necessary encoder not found.\n");
        return NULL;
    }

    AVCodecContext *encoder_ctx = avcodec_alloc_context3(encoder);
    if (!encoder_ctx)
    {
        fprintf(stderr, "Could not allocate an encoding context.\n");
        return NULL;
    }

    /* Set the basic encoder parameters. */
    encoder_ctx->sample_rate = dst_sample_rate; // Target sample rate
    encoder_ctx->ch_layout = dst_ch_layout;     // Target channel layout
    // av_channel_layout_default(&encoder_ctx->ch_layout, 2);
    // encoder_ctx->ch_layout.nb_channels = av_get_channel_layout_nb_channels(encoder_ctx->ch_layout);
    encoder_ctx->sample_fmt = (AV_SAMPLE_FMT_NONE == dst_sample_format) ? encoder->sample_fmts[0] : dst_sample_format;
    // Set to the first supported format: encoder->sample_fmts[0]
    encoder_ctx->bit_rate = bit_rate;

    return encoder_ctx;
}

int open_input_file(const char *filename,
                    AVFormatContext **input_fmt_ctx,
                    AVCodecContext **input_codec_ctx,
                    int *input_audio_stream_idx)
{
    /* Open the input file to read from it. */
    int ret = avformat_open_input(input_fmt_ctx, filename, NULL, NULL);
    if (ret < 0)
    {
        fprintf(stderr, "Could not open input file %s. Error: %s \n", filename, av_err2str(ret));
        *input_fmt_ctx = NULL;
        return ret;
    }

    // Find stream info
    /* Get information on the input file (number of streams etc.). */
    ret = avformat_find_stream_info(*input_fmt_ctx, NULL);
    if (ret < 0)
    {
        fprintf(stderr, "Could not find stream info. Error: %s\n", av_err2str(ret));
        avformat_close_input(input_fmt_ctx);
        return ret;
    }

    av_dump_format(*input_fmt_ctx, 0, NULL, 0);

    // Find audio stream
    ret = open_codec_context(input_audio_stream_idx, input_codec_ctx, *input_fmt_ctx, AVMEDIA_TYPE_AUDIO);
    if (ret < 0)
    {
        fprintf(stderr, "Could not open_codec_context. Error: %s\n", av_err2str(ret));
        return ret;
    }
    const AVStream *src_audio_stream = (*input_fmt_ctx)->streams[*input_audio_stream_idx];
    if (src_audio_stream == NULL)
    {
        fprintf(stderr, "Could not find audio stream. Audio stream index: %d\n", *input_audio_stream_idx);
        return -*input_audio_stream_idx;
    }

    return ret;
}

int open_output_file(const char *filename, AVCodecContext *input_codec_ctx, AVFormatContext **output_fmt_ctx)
{
    // Create output file
    int ret = avformat_alloc_output_context2(output_fmt_ctx, NULL, NULL, filename);
    if (ret < 0 || output_fmt_ctx == NULL)
    {
        fprintf(stderr, "Could not create output context. Error: %s\n", av_err2str(ret));
        return ret;
    }

    /* Create a new audio stream in the output file container. */
    AVStream *out_stream = avformat_new_stream(*output_fmt_ctx, NULL);
    if (out_stream == NULL)
    {
        fprintf(stderr, "Failed allocating output stream.\n");
        return AVERROR(ENOMEM);
    }
    fprintf(stderr, "----> 0 target audio  time_base.den=%d  time_base.num=%d\n", out_stream->time_base.den, out_stream->time_base.num);

    /* Set the sample rate for the container. */
    out_stream->time_base.den = input_codec_ctx->sample_rate;
    out_stream->time_base.num = 1;
    fprintf(stderr, "----> 1 target audio  time_base.den=%d  time_base.num=%d\n", out_stream->time_base.den, out_stream->time_base.num);

    /* Some container formats (like MP4) require global headers to be present.
     * Mark the encoder so that it behaves accordingly. */
    if ((*output_fmt_ctx)->oformat->flags & AVFMT_GLOBALHEADER)
        input_codec_ctx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;

    /* Open the encoder for the audio stream to use it later. */
    ret = avcodec_open2(input_codec_ctx, input_codec_ctx->codec, NULL);
    if (ret < 0)
    {
        fprintf(stderr, "Could not open output codec context. Error: %s\n", av_err2str(ret));
        return ret;
    }

    ret = avcodec_parameters_from_context(out_stream->codecpar, input_codec_ctx);
    if (ret < 0)
    {
        fprintf(stderr, "Could not initialize stream parameters. Error: %s\n", av_err2str(ret));
        return ret;
    }
    fprintf(stderr, "----> 2 target audio  time_base.den=%d  time_base.num=%d\n", out_stream->time_base.den, out_stream->time_base.num);

    if (!((*output_fmt_ctx)->oformat->flags & AVFMT_NOFILE))
    {
        ret = avio_open(&(*output_fmt_ctx)->pb, filename, AVIO_FLAG_WRITE);
        if (ret < 0)
        {
            fprintf(stderr, "Could not open output file. Error: %s\n", av_err2str(ret));
            return ret;
        }
    }

    fprintf(stderr, "----> 3 target audio  time_base.den=%d  time_base.num=%d\n", out_stream->time_base.den, out_stream->time_base.num);

    av_dump_format(*output_fmt_ctx, 0, NULL, 1);

    return 0;
}

int init_resampler(AVCodecContext *input_codec_context,
                   AVCodecContext *output_codec_context,
                   SwrContext **resample_context)
{
    int error;

    // // Initialize SwrContext for resampling
    // swr_ctx = swr_alloc();
    // if (swr_ctx == NULL)
    // {
    //     fprintf(stderr, "Could not allocate resampler context\n");
    //     exit(4);
    // }

    // av_opt_set_int(swr_ctx, "in_sample_rate", src_rate, 0);
    // av_opt_set_chlayout(swr_ctx, "in_chlayout", &src_ch_layout, 0);
    // av_opt_set_sample_fmt(swr_ctx, "in_sample_fmt", src_sample_fmt, 0);

    // av_opt_set_int(swr_ctx, "out_sample_rate", dst_rate, 0);
    // av_opt_set_chlayout(swr_ctx, "out_chlayout", &dst_ch_layout, 0);
    // av_opt_set_sample_fmt(swr_ctx, "out_sample_fmt", dst_sample_fmt, 0);

    /*
     * Create a resampler context for the conversion.
     * Set the conversion parameters.
     */
    error = swr_alloc_set_opts2(resample_context,
                                &output_codec_context->ch_layout,
                                output_codec_context->sample_fmt,
                                output_codec_context->sample_rate,
                                &input_codec_context->ch_layout,
                                input_codec_context->sample_fmt,
                                input_codec_context->sample_rate,
                                0, NULL);
    if (error < 0)
    {
        fprintf(stderr, "Could not allocate resample context\n");
        return error;
    }
    /*
     * Perform a sanity check so that the number of converted samples is
     * not greater than the number of samples to be converted.
     * If the sample rates differ, this case has to be handled differently
     */
    // av_assert0(output_codec_context->sample_rate == input_codec_context->sample_rate);

    /* Open the resampler with the specified parameters. */
    if ((error = swr_init(*resample_context)) < 0)
    {
        fprintf(stderr, "Could not open resample context\n");
        swr_free(resample_context);
        return error;
    }
    return 0;
}

int init_fifo(AVAudioFifo **fifo, AVCodecContext *output_codec_context)
{
    /* Create the FIFO buffer based on the specified output sample format. */
    if (!(*fifo = av_audio_fifo_alloc(output_codec_context->sample_fmt,
                                      output_codec_context->ch_layout.nb_channels, 1)))
    {
        fprintf(stderr, "Could not allocate FIFO\n");
        return AVERROR(ENOMEM);
    }
    return 0;
}

int write_output_file_header(AVFormatContext *output_format_context)
{
    int error;
    if ((error = avformat_write_header(output_format_context, NULL)) < 0)
    {
        fprintf(stderr, "Could not write output file header (error '%s')\n",
                av_err2str(error));
        return error;
    }
    return 0;
}

int decode_audio_frame(int in_audio_stream_idx,
                       AVFrame *frame,
                       AVFormatContext *input_format_context,
                       AVCodecContext *input_codec_context,
                       int *data_present, int *finished)
{
    /* Packet used for temporary storage. */
    AVPacket *input_packet;
    int error;

    error = init_packet(&input_packet);
    if (error < 0)
        return error;

    *data_present = 0;
    *finished = 0;
    /* Read one audio frame from the input file into a temporary packet. */
    while ((error = av_read_frame(input_format_context, input_packet)) >= 0)
    {
        if (input_packet->stream_index != in_audio_stream_idx)
        {
            av_packet_unref(input_packet);
            continue;
        }
        break;
    }

    if (error < 0)
    {
        /* If we are at the end of the file, flush the decoder below. */
        if (error == AVERROR_EOF)
            *finished = 1;
        else
        {
            fprintf(stderr, "Could not read frame (error '%s')\n",
                    av_err2str(error));
            goto cleanup;
        }
    }

    /* Send the audio frame stored in the temporary packet to the decoder.
     * The input audio stream decoder is used to do this. */
    if ((error = avcodec_send_packet(input_codec_context, input_packet)) < 0)
    {
        fprintf(stderr, "Could not send packet for decoding (error '%s')\n",
                av_err2str(error));
        goto cleanup;
    }

    /* Receive one frame from the decoder. */
    error = avcodec_receive_frame(input_codec_context, frame);
    /* If the decoder asks for more data to be able to decode a frame,
     * return indicating that no data is present. */
    if (error == AVERROR(EAGAIN))
    {
        error = 0;
        goto cleanup;
        /* If the end of the input file is reached, stop decoding. */
    }
    else if (error == AVERROR_EOF)
    {
        *finished = 1;
        error = 0;
        goto cleanup;
    }
    else if (error < 0)
    {
        fprintf(stderr, "Could not decode frame (error '%s')\n",
                av_err2str(error));
        goto cleanup;
        /* Default case: Return decoded data. */
    }
    else
    {
        *data_present = 1;
        goto cleanup;
    }

cleanup:
    av_packet_free(&input_packet);
    return error;
}

int init_converted_samples(uint8_t ***converted_input_samples,
                           AVCodecContext *output_codec_context,
                           int frame_size)
{
    int error;

    /* Allocate as many pointers as there are audio channels.
     * Each pointer will point to the audio samples of the corresponding
     * channels (although it may be NULL for interleaved formats).
     * Allocate memory for the samples of all channels in one consecutive
     * block for convenience. */
    if ((error = av_samples_alloc_array_and_samples(converted_input_samples, NULL,
                                                    output_codec_context->ch_layout.nb_channels,
                                                    frame_size,
                                                    output_codec_context->sample_fmt, 0)) < 0)
    {
        fprintf(stderr,
                "Could not allocate converted input samples (error '%s')\n",
                av_err2str(error));
        return error;
    }
    return 0;
}

int convert_samples(const uint8_t **input_data,
                    uint8_t **converted_data, const int frame_size,
                    SwrContext *resample_context)
{
    int ret;

    /* Convert the samples using the resampler. */
    if ((ret = swr_convert(resample_context,
                           converted_data, frame_size,
                           input_data, frame_size)) < 0)
    {
        fprintf(stderr, "Could not convert input samples (error '%s')\n",
                av_err2str(ret));
        return ret;
    }

    return ret;
}

int add_samples_to_fifo(AVAudioFifo *fifo,
                        uint8_t **converted_input_samples,
                        const int frame_size)
{
    // fprintf(stderr, "    ---> add_samples_to_fifo() av_audio_fifo_size(fifo)=%d  frame_size=%d\n", av_audio_fifo_size(fifo), frame_size);

    int error;

    /* Make the FIFO as large as it needs to be to hold both,
     * the old and the new samples. */
    if ((error = av_audio_fifo_realloc(fifo, av_audio_fifo_size(fifo) + frame_size)) < 0)
    {
        fprintf(stderr, "Could not reallocate FIFO\n");
        return error;
    }

    /* Store the new samples in the FIFO buffer. */
    if (av_audio_fifo_write(fifo, (void **)converted_input_samples, frame_size) < frame_size)
    {
        fprintf(stderr, "Could not write data to FIFO\n");
        return AVERROR_EXIT;
    }
    return 0;
}

int read_decode_convert_and_store(int in_audio_stream_idx,
                                  AVAudioFifo *fifo,
                                  AVFormatContext *input_format_context,
                                  AVCodecContext *input_codec_context,
                                  AVCodecContext *output_codec_context,
                                  SwrContext *resampler_context,
                                  int *finished)
{
    /* Temporary storage of the input samples of the frame read from the file. */
    AVFrame *input_frame = NULL;
    /* Temporary storage for the converted input samples. */
    uint8_t **converted_input_samples = NULL;
    int data_present;
    int ret = AVERROR_EXIT;

    /* Initialize temporary storage for one input frame. */
    if (init_input_frame(&input_frame))
        goto cleanup;
    /* Decode one frame worth of audio samples. */
    if (decode_audio_frame(in_audio_stream_idx, input_frame, input_format_context,
                           input_codec_context, &data_present, finished))
        goto cleanup;

    // // =====> DEBUG ONLY <===== Output decoded pcm data of input file.
    // const int resample_bytes_per_sample = av_get_bytes_per_sample(input_frame->format);
    // for (int i = 0; i < input_codec_context->frame_size; i++)
    // {
    //     for (int ch = 0; ch < input_frame->ch_layout.nb_channels; ch++)
    //     {
    //         fwrite(input_frame->extended_data[ch] + resample_bytes_per_sample * i, 1, resample_bytes_per_sample,
    //                input_audio_file);
    //     }
    // }
    // fprintf(stderr, "=====>>>>> input_codec_context->frame_size=%d\n", input_codec_context->frame_size);

    /* If we are at the end of the file and there are no more samples
     * in the decoder which are delayed, we are actually finished.
     * This must not be treated as an error. */
    if (*finished)
    {
        ret = 0;
        goto cleanup;
    }
    /* If there is decoded data, convert and store it. */
    if (data_present)
    {
        /* Initialize the temporary storage for the converted input samples. */
        if (init_converted_samples(&converted_input_samples, output_codec_context,
                                   input_frame->nb_samples))
            goto cleanup;

        /* Convert the input samples to the desired output sample format.
         * This requires a temporary storage provided by converted_input_samples. */
        ret = convert_samples((const uint8_t **)input_frame->extended_data, converted_input_samples,
                              input_frame->nb_samples, resampler_context);
        // fprintf(stderr, "    ---> samples_per_channel after converting=%d\n", ret);
        if (ret < 0)
            goto cleanup;

        /* Add the converted input samples to the FIFO buffer for later processing. */
        if (add_samples_to_fifo(fifo, converted_input_samples,
                                ret /* input_frame->nb_samples */))
            goto cleanup;
        ret = 0;
    }
    ret = 0;

cleanup:
    if (converted_input_samples)
        av_freep(&converted_input_samples[0]);
    av_freep(&converted_input_samples);
    av_frame_free(&input_frame);

    return ret;
}

int init_output_frame(AVFrame **frame,
                      AVCodecContext *output_codec_context,
                      int frame_size)
{
    int error;

    /* Create a new frame to store the audio samples. */
    if (!(*frame = av_frame_alloc()))
    {
        fprintf(stderr, "Could not allocate output frame\n");
        return AVERROR_EXIT;
    }

    /* Set the frame's parameters, especially its size and format.
     * av_frame_get_buffer needs this to allocate memory for the
     * audio samples of the frame.
     * Default channel layouts based on the number of channels
     * are assumed for simplicity. */
    (*frame)->nb_samples = frame_size;
    av_channel_layout_copy(&(*frame)->ch_layout, &output_codec_context->ch_layout);
    (*frame)->format = output_codec_context->sample_fmt;
    (*frame)->sample_rate = output_codec_context->sample_rate;

    /* Allocate the samples of the created frame. This call will make
     * sure that the audio frame can hold as many samples as specified. */
    if ((error = av_frame_get_buffer(*frame, 0)) < 0)
    {
        fprintf(stderr, "Could not allocate output frame samples (error '%s')\n",
                av_err2str(error));
        av_frame_free(frame);
        return error;
    }

    return 0;
}

int encode_audio_frame(AVFrame *frame,
                       AVFormatContext *output_format_context,
                       AVCodecContext *output_codec_context,
                       int *data_present)
{
    /* Packet used for temporary storage. */
    AVPacket *output_packet;
    int error;

    error = init_packet(&output_packet);
    if (error < 0)
        return error;

    /* Set a timestamp based on the sample rate for the container. */
    if (frame)
    {
        frame->pts = pts;
        pts += frame->nb_samples;
    }

    *data_present = 0;
    /* Send the audio frame stored in the temporary packet to the encoder.
     * The output audio stream encoder is used to do this. */
    error = avcodec_send_frame(output_codec_context, frame);
    /* Check for errors, but proceed with fetching encoded samples if the
     *  encoder signals that it has nothing more to encode. */
    if (error < 0 && error != AVERROR_EOF)
    {
        fprintf(stderr, "Could not send packet for encoding (error '%s')\n",
                av_err2str(error));
        goto cleanup;
    }

    /* Receive one encoded frame from the encoder. */
    error = avcodec_receive_packet(output_codec_context, output_packet);
    /* If the encoder asks for more data to be able to provide an
     * encoded frame, return indicating that no data is present. */
    if (error == AVERROR(EAGAIN))
    {
        error = 0;
        goto cleanup;
        /* If the last frame has been encoded, stop encoding. */
    }
    else if (error == AVERROR_EOF)
    {
        error = 0;
        goto cleanup;
    }
    else if (error < 0)
    {
        fprintf(stderr, "Could not encode frame (error '%s')\n",
                av_err2str(error));
        goto cleanup;
        /* Default case: Return encoded data. */
    }
    else
    {
        *data_present = 1;
    }

    /* Write one audio frame from the temporary packet to the output file. */
    if (*data_present &&
        (error = av_write_frame(output_format_context, output_packet)) < 0)
    {
        fprintf(stderr, "Could not write frame (error '%s')\n",
                av_err2str(error));
        goto cleanup;
    }
    fprintf(stderr, "audio_frame n:%d  pts:%ss (%lld)\n",
            audio_frame_count++, av_ts2timestr(pts, &output_codec_context->time_base), pts);

cleanup:
    av_packet_free(&output_packet);
    return error;
}

int load_encode_and_write(AVAudioFifo *fifo,
                          AVFormatContext *output_format_context,
                          AVCodecContext *output_codec_context)
{
    /* Temporary storage of the output samples of the frame written to the file. */
    AVFrame *output_frame;
    /* Use the maximum number of possible samples per frame.
     * If there is less than the maximum possible frame size in the FIFO
     * buffer use this number. Otherwise, use the maximum possible frame size. */
    const int frame_size = FFMIN(av_audio_fifo_size(fifo),
                                 output_codec_context->frame_size);
    // fprintf(stderr, "    ---> load_encode_and_write() av_audio_fifo_size(fifo)=%d  output_codec_context->frame_size=%d\n",
    //         av_audio_fifo_size(fifo),
    //         output_codec_context->frame_size);
    int data_written;

    /* Initialize temporary storage for one output frame. */
    if (init_output_frame(&output_frame, output_codec_context, frame_size))
        return AVERROR_EXIT;

    /* Read as many samples from the FIFO buffer as required to fill the frame.
     * The samples are stored in the frame temporarily. */
    if (av_audio_fifo_read(fifo, (void **)output_frame->data, frame_size) < frame_size)
    {
        fprintf(stderr, "Could not read data from FIFO\n");
        av_frame_free(&output_frame);
        return AVERROR_EXIT;
    }

    // // =====> DEBUG ONLY <===== Output resample pcm data of input file.
    // const int resample_bytes_per_sample = av_get_bytes_per_sample(output_frame->format);
    // for (int i = 0; i < frame_size; i++)
    // {
    //     for (int ch = 0; ch < output_frame->ch_layout.nb_channels; ch++)
    //     {
    //         fwrite(output_frame->extended_data[ch] + resample_bytes_per_sample * i, 1, resample_bytes_per_sample,
    //                output_audio_file);
    //     }
    // }

    /* Encode one frame worth of audio samples. */
    if (encode_audio_frame(output_frame, output_format_context,
                           output_codec_context, &data_written))
    {
        av_frame_free(&output_frame);
        return AVERROR_EXIT;
    }
    av_frame_free(&output_frame);
    return 0;
}

int write_output_file_trailer(AVFormatContext *output_format_context)
{
    int error;
    if ((error = av_write_trailer(output_format_context)) < 0)
    {
        fprintf(stderr, "Could not write output file trailer (error '%s')\n",
                av_err2str(error));
        return error;
    }
    return 0;
}