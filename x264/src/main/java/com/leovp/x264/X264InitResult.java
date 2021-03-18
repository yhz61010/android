package com.leovp.x264;

/**
 * Author: Michael Leo
 * Date: 21-3-18 下午4:21
 */
public class X264InitResult {

    public int err;
    public byte[] sps;
    public byte[] pps;

    public X264InitResult(int err, byte[] sps, byte[] pps) {
        this.err = err;
        this.sps = sps;
        this.pps = pps;
    }
}