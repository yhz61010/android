package com.leovp.ffmpeg.adpcm;

/**
 * Author: Michael Leo
 * Date: 2021/6/11 09:57
 */
public class AdpcmImaQtDecoder {

    public native int init(int sampleRate, int channels);

    public native void release();

    public native byte[] decode(byte[] adpcmBytes);

    public native String getVersion();

    static {
        System.loadLibrary("adpcm-ima-qt");
        System.loadLibrary("avcodec");
        System.loadLibrary("avutil");
    }
}