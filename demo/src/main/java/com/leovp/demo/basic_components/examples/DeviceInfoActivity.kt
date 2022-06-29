package com.leovp.demo.basic_components.examples

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.androidbase.utils.media.H264Util
import com.leovp.androidbase.utils.media.H265Util
import com.leovp.androidbase.utils.notch.INotchScreen
import com.leovp.androidbase.utils.notch.NotchScreenManager
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityDeviceInfoBinding
import com.leovp.lib_common_android.exts.getRealResolution
import com.leovp.lib_common_android.utils.DeviceUtil
import com.leovp.lib_json.toJsonString
import com.leovp.log_sdk.LogContext
import com.leovp.log_sdk.base.ITAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeviceInfoActivity : BaseDemonstrationActivity<ActivityDeviceInfoBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityDeviceInfoBinding {
        return ActivityDeviceInfoBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //        CodecUtil.getEncoderListByMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC).forEach { LogContext.log.i(TAG, "Name: ${it.name}") }
        CodecUtil.getAllSupportedCodecList().forEach { LogContext.log.i(TAG, "Name: ${it.name}") }

        val deviceInfo = DeviceUtil.getInstance(this).getDeviceInfo()
        binding.tv.text = deviceInfo
        LogContext.log.i(TAG, deviceInfo)

        NotchScreenManager.getInstance(this)
            .getNotchInfo(object : INotchScreen.NotchScreenCallback {
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

        lifecycleScope.launch(Dispatchers.IO) {
            for (index in 0 until DeviceUtil.getInstance(this@DeviceInfoActivity).cpuCoreCount) {
                val coreInfo =
                        DeviceUtil.getInstance(this@DeviceInfoActivity).getCpuCoreInfoByIndex(index)
                LogContext.log.i(TAG,
                    "cpu$index enable=${coreInfo?.online} minFreq=${coreInfo?.minFreq} maxFreq=${coreInfo?.maxFreq}")
            }
        }

        val sb: StringBuilder = StringBuilder()
        sb.append("=====> AVC <===============================")
        sb.append("\n")
        LogContext.log.i(TAG, "=====> AVC <===============================")
        H264Util.getAvcCodec().forEach {
            LogContext.log.i(TAG,
                "AVC Encoder : ${it.name.padEnd(25)} isSoftwareCodec=${CodecUtil.isSoftwareCodec(it.name)}")
            sb.append(it.name.padEnd(25))
            sb.append("\n")
        }
        H264Util.getAvcCodec(false).forEach {
            LogContext.log.i(TAG,
                "AVC Decoder : ${it.name.padEnd(25)} isSoftwareCodec=${CodecUtil.isSoftwareCodec(it.name)}")
            sb.append(it.name.padEnd(25))
            sb.append("\n")
        }
        sb.append("=====> HEVC <==============================")
        sb.append("\n")
        LogContext.log.i(TAG, "=====> HEVC <==============================")
        H265Util.getHevcCodec().forEach {
            LogContext.log.i(TAG,
                "HEVC Encoder: ${it.name.padEnd(25)} isSoftwareCodec=${CodecUtil.isSoftwareCodec(it.name)}")
            sb.append(it.name.padEnd(25))
            sb.append("\n")
        }
        H265Util.getHevcCodec(false).forEach {
            LogContext.log.i(TAG,
                "HEVC Decoder: ${it.name.padEnd(25)} isSoftwareCodec=${CodecUtil.isSoftwareCodec(it.name)}")
            sb.append(it.name.padEnd(25))
            sb.append("\n")
        }

        sb.append("===========================================")
        sb.append("\n")
        binding.tv3.text = sb.toString()
        LogContext.log.e(TAG, "===========================================\n$sb")
    }

    companion object {
        private const val TAG = "DeviceInfo"
    }
}