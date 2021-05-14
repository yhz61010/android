package com.leovp.androidbase.utils.media;

/**
 * https://blog.csdn.net/duoluo9/article/details/85318274
 * <p>
 * val spsByteArray = byteArrayOf(103, 66, -128, 31, -23, 3, -64, -41, 64, 54, -123, 9, -88)
 * val h264Parser = H264SPSParser()
 * val h264Frame = SpsFrame().getSpsFrame(spsByteArray)
 * println("width=${h264Parser.getWidth(h264Frame)}")
 * println("height=${h264Parser.getHeight(h264Frame)}")
 */
@Deprecated
public class H264SPSParser {
    private int startBit = 0;

    public int getStartBit() {
        return startBit;
    }

    /*
     * 从数据流data中第StartBit位开始读，读bitCnt位，以无符号整形返回
     */
    public short u(byte[] data, int bitCnt, int StartBit) {
        short ret = 0;
        int start = StartBit;
        for (int i = 0; i < bitCnt; i++) {
            ret <<= 1;
            if ((data[start / 8] & (0x80 >> (start % 8))) != 0) {
                ret += 1;
            }
            start++;
        }
        startBit = StartBit + bitCnt;
        return ret;
    }

    /*
     * 无符号指数哥伦布编码
     * leadingZeroBits = ?1;
     * for( b = 0; !b; leadingZeroBits++ )
     *    b = read_bits( 1 )
     * 变量codeNum 按照如下方式赋值：
     * codeNum = 2^leadingZeroBits ? 1 + read_bits( leadingZeroBits )
     * 这里read_bits( leadingZeroBits )的返回值使用高位在先的二进制无符号整数表示。
     */
    public short ue(byte[] data, int StartBit) {
        short ret = 0;
        int leadingZeroBits = -1;
        int tempStartBit = StartBit;
        for (int b = 0; b != 1; leadingZeroBits++) {//读到第一个不为0的数，计算前面0的个数
            b = u(data, 1, tempStartBit++);
        }
        ret = (short) (Math.pow(2, leadingZeroBits) - 1 + u(data, leadingZeroBits, tempStartBit));
        startBit = tempStartBit + leadingZeroBits;
        return ret;
    }

    /*
     * 有符号指数哥伦布编码
     * 9.1.1 有符号指数哥伦布编码的映射过程
     *按照9.1节规定，本过程的输入是codeNum。
     *本过程的输出是se(v)的值。
     *表9-3中给出了分配给codeNum的语法元素值的规则，语法元素值按照绝对值的升序排列，负值按照其绝对
     *值参与排列，但列在绝对值相等的正值之后。
     *表 9-3－有符号指数哥伦布编码语法元素se(v)值与codeNum的对应
     *codeNum 语法元素值
     *  0       0
     *  1       1
     *  2       ?1
     *  3       2
     *  4       ?2
     *  5       3
     *  6       ?3
     *  k       (?1)^(k+1) Ceil( k&pide;2 )
     */
    public int se(byte[] data, int StartBit) {
        int ret = 0;
        short codeNum = ue(data, StartBit);
        ret = (int) (Math.pow(-1, codeNum + 1) * Math.ceil(codeNum / 2));
        return ret;
    }

    /**
     * chroma_format_idc = 0，表示 色彩为单色
     * chroma_format_idc = 1，表示 YUV 4:2:0
     * chroma_format_idc = 2，表示 YUV 4:2:2
     * chroma_format_idc = 3，表示 YUV 4:4:4
     */
    public int getWidth(SpsFrame sps) {
        int width = (sps.getPic_width_in_mbs_minus_1() + 1) * 16;

        if (sps.getFrame_cropping_flag() == 1) {
            int crop_unit_x;
            if (0 == sps.getChroma_format_idc()) {
                crop_unit_x = 1;
            } else if (1 == sps.getChroma_format_idc()) {
                crop_unit_x = 2;
            } else if (2 == sps.getChroma_format_idc()) {
                crop_unit_x = 2;
            } else {
                crop_unit_x = 1;
            }

            width -= crop_unit_x * (sps.getFrame_crop_left_offset() + sps.getFrame_crop_right_offset());
        }
        return width;
    }

    /**
     * chroma_format_idc = 0，表示 色彩为单色
     * chroma_format_idc = 1，表示 YUV 4:2:0
     * chroma_format_idc = 2，表示 YUV 4:2:2
     * chroma_format_idc = 3，表示 YUV 4:4:4
     */
    public int getHeight(SpsFrame sps) {
        int height = (2 - sps.getFrame_mbs_only_flag()) * (sps.getPic_height_in_map_units_minus_1() + 1) * 16;

        if (sps.getFrame_cropping_flag() == 1) {
            int crop_unit_y;
            if (0 == sps.getChroma_format_idc()) {
                crop_unit_y = 2 - sps.getFrame_mbs_only_flag();
            } else if (1 == sps.getChroma_format_idc()) {
                crop_unit_y = 2 * (2 - sps.getFrame_mbs_only_flag());
            } else if (2 == sps.getChroma_format_idc()) {
                crop_unit_y = 2 - sps.getFrame_mbs_only_flag();
            } else {
                crop_unit_y = 2 - sps.getFrame_mbs_only_flag();
            }
            height -= crop_unit_y * (sps.getFrame_crop_top_offset() + sps.getFrame_crop_bottom_offset());
        }
        return height;
    }
}
