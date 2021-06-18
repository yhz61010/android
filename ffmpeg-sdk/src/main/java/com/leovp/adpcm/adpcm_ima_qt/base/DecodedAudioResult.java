package com.leovp.adpcm.adpcm_ima_qt.base;

/**
 * Author: Michael Leo
 * Date: 2021/6/18 11:23
 */
public class DecodedAudioResult {
    private byte[] leftChannelData;
    private byte[] rightChannelData;

    public byte[] getLeftChannelData() {
        return leftChannelData;
    }

    public void setLeftChannelData(byte[] leftChannelData) {
        this.leftChannelData = leftChannelData;
    }

    public byte[] getRightChannelData() {
        return rightChannelData;
    }

    public void setRightChannelData(byte[] rightChannelData) {
        this.rightChannelData = rightChannelData;
    }
}
