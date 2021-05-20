package com.leovp.x264;

/**
 * Author: Michael Leo
 * Date: 21-3-18 下午4:21
 */
public class X264Params {

    public static final int CSP_I420 = 0x0001; // yuv 4:2:0 planar
    public static final int CSP_YV12 = 0x0002; // yvu 4:2:0 planar
    public static final int CSP_NV12 = 0x0003; // yuv 4:2:0, with one y plane and one packed u+v
    public static final int CSP_NV21 = 0x0004; // yuv 4:2:0, with one y plane and one packed v+u
    public int width = 1280;
    public int height = 720;
    public int bitrate = 500;
    public int fps = 24;
    public int gop = 48;
    public String profile = "baseline";
    public String preset = "ultrafast";
}