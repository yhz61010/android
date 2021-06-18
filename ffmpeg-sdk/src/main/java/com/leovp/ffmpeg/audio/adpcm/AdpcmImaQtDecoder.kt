package com.leovp.ffmpeg.audio.adpcm;

import com.leovp.ffmpeg.base.DecodedAudioResult;

/**
 * Author: Michael Leo
 * Date: 2021/6/11 09:57
 */
public class AdpcmImaQtDecoder {

    private AdpcmImaQtDecoder() {
    }

    public AdpcmImaQtDecoder(int sampleRate, int channels) {
        init(sampleRate, channels);
    }

    private native int init(int sampleRate, int channels);

    public native void release();

    public native DecodedAudioResult decode(byte[] adpcmBytes);

    public native String getVersion();

    /**
     * In QuickTime, IMA is encoded by chunks of 34 bytes (=64 samples).
     * Channel data is interleaved per-chunk.
     * <p>
     * The return result is 34 bytes * channels
     */
    public native int chunkSize();

    static {
        System.loadLibrary("adpcm-ima-qt");
        System.loadLibrary("avcodec");
        System.loadLibrary("avutil");
    }
}