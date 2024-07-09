#include "cmn_util.h"

#include <stddef.h>
#include <stdio.h>
#include <libavutil/macros.h>
#include <libavutil/samplefmt.h>

double r2d(AVRational r)
{
    return r.den == 0 ? 0 : (double)r.num / (double)r.den;
}

/**
 * Usage:
 * @code
 * const char* fmt = get_format_from_sample_fmt(sfmt);
 * fprintf(stderr, "The format is %s", fmt);
 * // Example: s16le, f32le, h264, hevc, mjpeg
 * @endcode
 *
 * @param sample_fmt
 * @return >= 0 in case of success, a negative code in case of failure
 *
 * @note This result can be used by `-f` option of `ffmepg`.
 * @note This result is part of `ffmpeg -formats`.
 */
const char* get_format_from_sample_fmt(const enum AVSampleFormat sample_fmt)
{
    const struct sample_fmt_entry
    {
        enum AVSampleFormat sample_fmt;
        const char *fmt_be, *fmt_le;
    } sample_fmt_entries[] = {
        {AV_SAMPLE_FMT_U8, "u8", "u8"},
        {AV_SAMPLE_FMT_S16, "s16be", "s16le"},
        {AV_SAMPLE_FMT_S32, "s32be", "s32le"},
        {AV_SAMPLE_FMT_FLT, "f32be", "f32le"},
        {AV_SAMPLE_FMT_DBL, "f64be", "f64le"},
    };

    for (int i = 0; i < FF_ARRAY_ELEMS(sample_fmt_entries); i++)
    {
        const struct sample_fmt_entry* entry = &sample_fmt_entries[i];
        if (sample_fmt == entry->sample_fmt)
        {
            return AV_NE(entry->fmt_be, entry->fmt_le);
        }
    }

    fprintf(stderr,
            "sample format %s is not supported as output format\n",
            av_get_sample_fmt_name(sample_fmt));
    return NULL;
}

// av_get_sample_fmt_name(smft)

int get_packed_sample_fmt(enum AVSampleFormat sfmt)
{
    if (av_sample_fmt_is_planar(sfmt))
    {
        const char* packed = av_get_sample_fmt_name(sfmt);
        fprintf(stderr, "Warning: sample format is planar %s(%d).\n", packed ? packed : "?", sfmt);
        sfmt = av_get_packed_sample_fmt(sfmt);
    }
    return sfmt;
}
