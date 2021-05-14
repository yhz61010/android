package com.leovp.androidbase.utils.media;

import org.jetbrains.annotations.NotNull;

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
public class SpsFrame {
    private int forbidden_zero_bit;
    private int nal_ref_idc;
    private int nal_unit_type;
    private int profile_idc;
    private int constraint_set0_flag;
    private int constraint_set1_flag;
    private int constraint_set2_flag;
    private int constraint_set3_flag;
    private int reserved_zero_4bits;
    private int level_idc;
    private int seq_parameter_set_id;
    //if(profile_idc == 100||profile_idc == 110||profile_idc == 122||profile_idc == 144){
    private int chroma_format_idc;
    //if(chroma_format_idc == 3){
    private int residual_colour_transform_flag;
    //}
    private int bit_depth_luma_minus8;
    private int bit_depth_chroma_minus8;
    private int qpprime_y_zero_transform_bypass_flag;
    private int seq_scaling_matrix_present_flag;
    //if(seq_scaling_matrix_present_flag ==1 ){
    //for(8)
    private int[] seq_scaling_list_present_flag = new int[8];
    //}}
    private int log2_max_frame_num_minus4;
    private int pic_order_cnt_type;
    //if(pic_order_cnt_type == 0)
    private int log2_max_pic_order_cnt_lsb_minus4;
    //else if(pic_order_cnt_type == 1){
    private int delta_pic_order_always_zero_flag;
    private int offset_for_non_ref_pic;
    private int offset_for_top_to_bottom_field;
    private int getNum_ref_frames_in_pic_prder_cnt_cycle;
    //for(getNum_ref_frames_in_pic_prder_cnt_cycle)
    private int[] offset_for_ref_frame;
    //}
    private int num_ref_frames;
    private int gaps_in_frame_num_value_allowed_flag;
    private int pic_width_in_mbs_minus_1;
    private int pic_height_in_map_units_minus_1;
    private int frame_mbs_only_flag;
    //if(frame_mbs_only_flag == 0){
    private int mb_adaptive_frame_field_flag;
    //}
    private int direct_8x8_inference_flag;
    private int frame_cropping_flag;
    //if(frame_cropping_flag == 1){
    private int frame_crop_left_offset;
    private int frame_crop_right_offset;
    private int frame_crop_top_offset;
    private int frame_crop_bottom_offset;
    // }
    private int vui_prameters_present_flag;
    //if(vui_prameters_present_flag == 1){
    private int vui_parameters;
    private int rbsp_stop_one_bit;
    // }


    public SpsFrame getSpsFrame(byte[] sps) {
        H264SPSParser h264SPSPaser = new H264SPSParser();
        forbidden_zero_bit = h264SPSPaser.u(sps, 1, 0);
        nal_ref_idc = h264SPSPaser.u(sps, 2, h264SPSPaser.getStartBit());
        nal_unit_type = h264SPSPaser.u(sps, 5, h264SPSPaser.getStartBit());
        profile_idc = h264SPSPaser.u(sps, 8, h264SPSPaser.getStartBit());
        constraint_set0_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
        constraint_set1_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
        constraint_set2_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
        constraint_set3_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
        reserved_zero_4bits = h264SPSPaser.u(sps, 4, h264SPSPaser.getStartBit());
        level_idc = h264SPSPaser.u(sps, 8, h264SPSPaser.getStartBit());
        seq_parameter_set_id = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
        if (profile_idc == 100 || profile_idc == 110 || profile_idc == 122 || profile_idc == 144) {
            chroma_format_idc = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
            if (chroma_format_idc == 3) {
                residual_colour_transform_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
            }
            bit_depth_luma_minus8 = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
            bit_depth_chroma_minus8 = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
            qpprime_y_zero_transform_bypass_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
            seq_scaling_matrix_present_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
            if (seq_scaling_matrix_present_flag == 1) {
                seq_scaling_list_present_flag = new int[8];
                for (int i = 0; i < 8; i++) {
                    seq_scaling_list_present_flag[i] = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
                }
            }
        }
        log2_max_frame_num_minus4 = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
        pic_order_cnt_type = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
        if (pic_order_cnt_type == 0)
            log2_max_pic_order_cnt_lsb_minus4 = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
        else if (pic_order_cnt_type == 1) {
            delta_pic_order_always_zero_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
            offset_for_non_ref_pic = h264SPSPaser.se(sps, h264SPSPaser.getStartBit());
            offset_for_top_to_bottom_field = h264SPSPaser.se(sps, h264SPSPaser.getStartBit());
            getNum_ref_frames_in_pic_prder_cnt_cycle = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
            offset_for_ref_frame = new int[getNum_ref_frames_in_pic_prder_cnt_cycle];
            for (int i = 0; i < getNum_ref_frames_in_pic_prder_cnt_cycle; i++) {
                offset_for_ref_frame[i] = h264SPSPaser.se(sps, h264SPSPaser.getStartBit());
            }
        }
        num_ref_frames = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
        gaps_in_frame_num_value_allowed_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
        pic_width_in_mbs_minus_1 = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
        pic_height_in_map_units_minus_1 = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
        frame_mbs_only_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
        if (frame_mbs_only_flag == 0) {
            mb_adaptive_frame_field_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
        }
        direct_8x8_inference_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
        frame_cropping_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
        if (frame_cropping_flag == 1) {
            frame_crop_left_offset = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
            frame_crop_right_offset = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
            frame_crop_top_offset = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
            frame_crop_bottom_offset = h264SPSPaser.ue(sps, h264SPSPaser.getStartBit());
        }
        vui_prameters_present_flag = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
        if (vui_prameters_present_flag == 1) {
            vui_parameters = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
        }
        rbsp_stop_one_bit = h264SPSPaser.u(sps, 1, h264SPSPaser.getStartBit());
        return this;
    }

    public int getForbidden_zero_bit() {
        return forbidden_zero_bit;
    }

    public void setForbidden_zero_bit(int forbidden_zero_bit) {
        this.forbidden_zero_bit = forbidden_zero_bit;
    }

    public int getNal_ref_idc() {
        return nal_ref_idc;
    }

    public void setNal_ref_idc(int nal_ref_idc) {
        this.nal_ref_idc = nal_ref_idc;
    }

    public int getNal_unit_type() {
        return nal_unit_type;
    }

    public void setNal_unit_type(int nal_unit_type) {
        this.nal_unit_type = nal_unit_type;
    }

    public int getProfile_idc() {
        return profile_idc;
    }

    public void setProfile_idc(int profile_idc) {
        this.profile_idc = profile_idc;
    }

    public int getConstraint_set0_flag() {
        return constraint_set0_flag;
    }

    public void setConstraint_set0_flag(int constraint_set0_flag) {
        this.constraint_set0_flag = constraint_set0_flag;
    }

    public int getConstraint_set1_flag() {
        return constraint_set1_flag;
    }

    public void setConstraint_set1_flag(int constraint_set1_flag) {
        this.constraint_set1_flag = constraint_set1_flag;
    }

    public int getConstraint_set2_flag() {
        return constraint_set2_flag;
    }

    public void setConstraint_set2_flag(int constraint_set2_flag) {
        this.constraint_set2_flag = constraint_set2_flag;
    }

    public int getConstraint_set3_flag() {
        return constraint_set3_flag;
    }

    public void setConstraint_set3_flag(int constraint_set3_flag) {
        this.constraint_set3_flag = constraint_set3_flag;
    }

    public int getReserved_zero_4bits() {
        return reserved_zero_4bits;
    }

    public void setReserved_zero_4bits(int reserved_zero_4bits) {
        this.reserved_zero_4bits = reserved_zero_4bits;
    }

    public int getLevel_idc() {
        return level_idc;
    }

    public void setLevel_idc(int level_idc) {
        this.level_idc = level_idc;
    }

    public int getSeq_parameter_set_id() {
        return seq_parameter_set_id;
    }

    public void setSeq_parameter_set_id(int seq_parameter_set_id) {
        this.seq_parameter_set_id = seq_parameter_set_id;
    }

    public int getChroma_format_idc() {
        return chroma_format_idc;
    }

    public void setChroma_format_idc(int chroma_format_idc) {
        this.chroma_format_idc = chroma_format_idc;
    }

    public int getResidual_colour_transform_flag() {
        return residual_colour_transform_flag;
    }

    public void setResidual_colour_transform_flag(int residual_colour_transform_flag) {
        this.residual_colour_transform_flag = residual_colour_transform_flag;
    }

    public int getBit_depth_luma_minus8() {
        return bit_depth_luma_minus8;
    }

    public void setBit_depth_luma_minus8(int bit_depth_luma_minus8) {
        this.bit_depth_luma_minus8 = bit_depth_luma_minus8;
    }

    public int getBit_depth_chroma_minus8() {
        return bit_depth_chroma_minus8;
    }

    public void setBit_depth_chroma_minus8(int bit_depth_chroma_minus8) {
        this.bit_depth_chroma_minus8 = bit_depth_chroma_minus8;
    }

    public int getQpprime_y_zero_transform_bypass_flag() {
        return qpprime_y_zero_transform_bypass_flag;
    }

    public void setQpprime_y_zero_transform_bypass_flag(int qpprime_y_zero_transform_bypass_flag) {
        this.qpprime_y_zero_transform_bypass_flag = qpprime_y_zero_transform_bypass_flag;
    }

    public int getSeq_scaling_matrix_present_flag() {
        return seq_scaling_matrix_present_flag;
    }

    public void setSeq_scaling_matrix_present_flag(int seq_scaling_matrix_present_flag) {
        this.seq_scaling_matrix_present_flag = seq_scaling_matrix_present_flag;
    }

    public int[] getSeq_scaling_list_present_flag() {
        return seq_scaling_list_present_flag;
    }

    public void setSeq_scaling_list_present_flag(int[] seq_scaling_list_present_flag) {
        this.seq_scaling_list_present_flag = seq_scaling_list_present_flag;
    }

    public int getLog2_max_frame_num_minus4() {
        return log2_max_frame_num_minus4;
    }

    public void setLog2_max_frame_num_minus4(int log2_max_frame_num_minus4) {
        this.log2_max_frame_num_minus4 = log2_max_frame_num_minus4;
    }

    public int getPic_order_cnt_type() {
        return pic_order_cnt_type;
    }

    public void setPic_order_cnt_type(int pic_order_cnt_type) {
        this.pic_order_cnt_type = pic_order_cnt_type;
    }

    public int getLog2_max_pic_order_cnt_lsb_minus4() {
        return log2_max_pic_order_cnt_lsb_minus4;
    }

    public void setLog2_max_pic_order_cnt_lsb_minus4(int log2_max_pic_order_cnt_lsb_minus4) {
        this.log2_max_pic_order_cnt_lsb_minus4 = log2_max_pic_order_cnt_lsb_minus4;
    }

    public int getDelta_pic_order_always_zero_flag() {
        return delta_pic_order_always_zero_flag;
    }

    public void setDelta_pic_order_always_zero_flag(int delta_pic_order_always_zero_flag) {
        this.delta_pic_order_always_zero_flag = delta_pic_order_always_zero_flag;
    }

    public int getOffset_for_non_ref_pic() {
        return offset_for_non_ref_pic;
    }

    public void setOffset_for_non_ref_pic(int offset_for_non_ref_pic) {
        this.offset_for_non_ref_pic = offset_for_non_ref_pic;
    }

    public int getOffset_for_top_to_bottom_field() {
        return offset_for_top_to_bottom_field;
    }

    public void setOffset_for_top_to_bottom_field(int offset_for_top_to_bottom_field) {
        this.offset_for_top_to_bottom_field = offset_for_top_to_bottom_field;
    }

    public int getGetNum_ref_frames_in_pic_prder_cnt_cycle() {
        return getNum_ref_frames_in_pic_prder_cnt_cycle;
    }

    public void setGetNum_ref_frames_in_pic_prder_cnt_cycle(int getNum_ref_frames_in_pic_prder_cnt_cycle) {
        this.getNum_ref_frames_in_pic_prder_cnt_cycle = getNum_ref_frames_in_pic_prder_cnt_cycle;
    }

    public int[] getOffset_for_ref_frame() {
        return offset_for_ref_frame;
    }

    public void setOffset_for_ref_frame(int[] offset_for_ref_frame) {
        this.offset_for_ref_frame = offset_for_ref_frame;
    }

    public int getNum_ref_frames() {
        return num_ref_frames;
    }

    public void setNum_ref_frames(int num_ref_frames) {
        this.num_ref_frames = num_ref_frames;
    }

    public int getGaps_in_frame_num_value_allowed_flag() {
        return gaps_in_frame_num_value_allowed_flag;
    }

    public void setGaps_in_frame_num_value_allowed_flag(int gaps_in_frame_num_value_allowed_flag) {
        this.gaps_in_frame_num_value_allowed_flag = gaps_in_frame_num_value_allowed_flag;
    }

    public int getPic_width_in_mbs_minus_1() {
        return pic_width_in_mbs_minus_1;
    }

    public void setPic_width_in_mbs_minus_1(int pic_width_in_mbs_minus_1) {
        this.pic_width_in_mbs_minus_1 = pic_width_in_mbs_minus_1;
    }

    public int getPic_height_in_map_units_minus_1() {
        return pic_height_in_map_units_minus_1;
    }

    public void setPic_height_in_map_units_minus_1(int pic_height_in_map_units_minus_1) {
        this.pic_height_in_map_units_minus_1 = pic_height_in_map_units_minus_1;
    }

    public int getFrame_mbs_only_flag() {
        return frame_mbs_only_flag;
    }

    public void setFrame_mbs_only_flag(int frame_mbs_only_flag) {
        this.frame_mbs_only_flag = frame_mbs_only_flag;
    }

    public int getMb_adaptive_frame_field_flag() {
        return mb_adaptive_frame_field_flag;
    }

    public void setMb_adaptive_frame_field_flag(int mb_adaptive_frame_field_flag) {
        this.mb_adaptive_frame_field_flag = mb_adaptive_frame_field_flag;
    }

    public int getDirect_8x8_inference_flag() {
        return direct_8x8_inference_flag;
    }

    public void setDirect_8x8_inference_flag(int direct_8x8_inference_flag) {
        this.direct_8x8_inference_flag = direct_8x8_inference_flag;
    }

    public int getFrame_cropping_flag() {
        return frame_cropping_flag;
    }

    public void setFrame_cropping_flag(int frame_cropping_flag) {
        this.frame_cropping_flag = frame_cropping_flag;
    }

    public int getFrame_crop_left_offset() {
        return frame_crop_left_offset;
    }

    public void setFrame_crop_left_offset(int frame_crop_left_offset) {
        this.frame_crop_left_offset = frame_crop_left_offset;
    }

    public int getFrame_crop_right_offset() {
        return frame_crop_right_offset;
    }

    public void setFrame_crop_right_offset(int frame_crop_right_offset) {
        this.frame_crop_right_offset = frame_crop_right_offset;
    }

    public int getFrame_crop_top_offset() {
        return frame_crop_top_offset;
    }

    public void setFrame_crop_top_offset(int frame_crop_top_offset) {
        this.frame_crop_top_offset = frame_crop_top_offset;
    }

    public int getFrame_crop_bottom_offset() {
        return frame_crop_bottom_offset;
    }

    public void setFrame_crop_bottom_offset(int frame_crop_bottom_offset) {
        this.frame_crop_bottom_offset = frame_crop_bottom_offset;
    }

    public int getVui_prameters_present_flag() {
        return vui_prameters_present_flag;
    }

    public void setVui_prameters_present_flag(int vui_prameters_present_flag) {
        this.vui_prameters_present_flag = vui_prameters_present_flag;
    }

    public int getVui_parameters() {
        return vui_parameters;
    }

    public void setVui_parameters(int vui_parameters) {
        this.vui_parameters = vui_parameters;
    }

    public int getRbsp_stop_one_bit() {
        return rbsp_stop_one_bit;
    }

    public void setRbsp_stop_one_bit(int rbsp_stop_one_bit) {
        this.rbsp_stop_one_bit = rbsp_stop_one_bit;
    }

    @NotNull
    @Override
    public String toString() {
        return String.valueOf(forbidden_zero_bit) + "," +
                String.valueOf(nal_ref_idc) + "," +
                String.valueOf(nal_unit_type) + "," +
                String.valueOf(profile_idc) + "," +
                String.valueOf(constraint_set0_flag) + "," +
                String.valueOf(constraint_set1_flag) + "," +
                String.valueOf(constraint_set2_flag) + "," +
                String.valueOf(constraint_set3_flag) + "," +
                String.valueOf(reserved_zero_4bits) + "," +
                String.valueOf(level_idc) + "," +
                String.valueOf(seq_parameter_set_id) + "," +
                String.valueOf(log2_max_frame_num_minus4) + "," +
                String.valueOf(pic_order_cnt_type) + "," +
                String.valueOf(log2_max_pic_order_cnt_lsb_minus4) + "," +
                String.valueOf(num_ref_frames) + "," +
                String.valueOf(gaps_in_frame_num_value_allowed_flag) + "," +
                String.valueOf(pic_width_in_mbs_minus_1) + "," +
                String.valueOf(pic_height_in_map_units_minus_1) + "," +
                String.valueOf(frame_mbs_only_flag) + "," +
                String.valueOf(direct_8x8_inference_flag) + "," +
                String.valueOf(frame_cropping_flag) + "," +
                String.valueOf(vui_prameters_present_flag) + "," +
                String.valueOf(rbsp_stop_one_bit);
    }
}