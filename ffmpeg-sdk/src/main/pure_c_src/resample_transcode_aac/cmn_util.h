#ifndef CMN_UTIL_H
#define CMN_UTIL_H
#include <libavutil/rational.h>
#include <libavutil/samplefmt.h>

double r2d(AVRational r);
const char* get_format_from_sample_fmt(const enum AVSampleFormat sample_fmt);
int get_packed_sample_fmt(enum AVSampleFormat sfmt);

#endif //CMN_UTIL_H
