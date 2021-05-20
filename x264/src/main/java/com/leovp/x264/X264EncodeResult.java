package com.leovp.x264;

/**
 * Author: Michael Leo
 * Date: 21-3-18 下午4:21
 */
public class X264EncodeResult {

    public int err;
    public byte[] data;
    public long pts;
    public boolean isKey;

    public X264EncodeResult(int err, byte[] data, long pts, boolean isKey) {
        this.err = err;
        this.data = data;
        this.pts = pts;
        this.isKey = isKey;
    }
}
