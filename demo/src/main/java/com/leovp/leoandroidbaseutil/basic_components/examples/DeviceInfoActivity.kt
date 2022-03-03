package com.leovp.leoandroidbaseutil.basic_components.examples

import android.annotation.SuppressLint
import android.os.Bundle
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.androidbase.utils.media.H264Util
import com.leovp.androidbase.utils.media.H265Util
import com.leovp.androidbase.utils.notch.INotchScreen
import com.leovp.androidbase.utils.notch.NotchScreenManager
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityDeviceInfoBinding
import com.leovp.lib_common_android.exts.getRealResolution
import com.leovp.lib_common_android.utils.DeviceUtil
import com.leovp.log_sdk.LogContext

class DeviceInfoActivity : BaseDemonstrationActivity() {

    private lateinit var binding: ActivityDeviceInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceInfoBinding.inflate(layoutInflater).apply { setContentView(root) }

        //        CodecUtil.getEncoderListByMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC).forEach { LogContext.log.i(TAG, "Name: ${it.name}") }
        CodecUtil.getAllSupportedCodecList().forEach { LogContext.log.i(TAG, "Name: ${it.name}") }

        val deviceInfo = DeviceUtil.getInstance(this).getDeviceInfo()
        binding.tv.text = deviceInfo
        LogContext.log.i(TAG, deviceInfo)

        NotchScreenManager.getInstance(this).getNotchInfo(object : INotchScreen.NotchScreenCallback {
            @SuppressLint("SetTextI18n")
            override fun onResult(notchScreenInfo: INotchScreen.NotchScreenInfo) {
                LogContext.log.i(TAG, "notchScreenInfo: ${notchScreenInfo.toJsonString()}")
                binding.tv2.text = "notchScreenInfo: ${notchScreenInfo.toJsonString()}"
                notchScreenInfo.notchRects?.let {
                    val halfScreenWidth = getRealResolution().width / 2
                    if (it[0].left < halfScreenWidth && halfScreenWidth < it[0].right) {
                        LogContext.log.i(TAG, "Notch in Middle")
                        binding.tv2.text = "Notch in Middle"
                    } else if (halfScreenWidth < it[0].left) {
                        LogContext.log.i(TAG, "Notch in Right")
                        binding.tv2.text = "Notch in Right"
                    } else {
                        LogContext.log.i(TAG, "Notch in Left")
                        binding.tv2.text = "Notch in Left"
                    }
                }
            }
        })

        for (index in 0 until DeviceUtil.getInstance(this).cpuCoreCount) {
            val coreInfo = DeviceUtil.getInstance(this).getCpuCoreInfoByIndex(index)
            LogContext.log.i(TAG, "cpu$index enable=${coreInfo?.online} minFreq=${coreInfo?.minFreq} maxFreq=${coreInfo?.maxFreq}")
        }


        LogContext.log.i(TAG, "=====> HEVC <==============================")
        H265Util.getHevcCodec().forEach { LogContext.log.i(TAG, "HEVC Encoder: ${it.name.padEnd(25)} isSoftwareCodec=${CodecUtil.isSoftwareCodec(it.name)}") }
        H265Util.getHevcCodec(false).forEach { LogContext.log.i(TAG, "HEVC Decoder: ${it.name.padEnd(25)} isSoftwareCodec=${CodecUtil.isSoftwareCodec(it.name)}") }

        LogContext.log.i(TAG, "=====> AVC <===============================")
        H264Util.getAvcCodec().forEach { LogContext.log.i(TAG, "AVC Encoder : ${it.name.padEnd(25)} isSoftwareCodec=${CodecUtil.isSoftwareCodec(it.name)}") }
        H264Util.getAvcCodec(false).forEach { LogContext.log.i(TAG, "AVC Decoder : ${it.name.padEnd(25)} isSoftwareCodec=${CodecUtil.isSoftwareCodec(it.name)}") }

        LogContext.log.i(TAG, "===========================================")
    }

    companion object {
        private const val TAG = "DeviceInfo"
    }
}