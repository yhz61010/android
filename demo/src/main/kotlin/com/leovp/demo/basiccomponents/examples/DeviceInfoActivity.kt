package com.leovp.demo.basiccomponents.examples

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.leovp.android.utils.DeviceUtil
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.androidbase.utils.media.H264Util
import com.leovp.androidbase.utils.media.H265Util
import com.leovp.androidbase.utils.notch.DisplayCutoutManager
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityDeviceInfoBinding
import com.leovp.json.toJsonString
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeviceInfoActivity : BaseDemonstrationActivity<ActivityDeviceInfoBinding>(R.layout.activity_device_info) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityDeviceInfoBinding =
        ActivityDeviceInfoBinding.inflate(layoutInflater)

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //        CodecUtil.getEncoderListByMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC).forEach { LogContext.log.i(TAG, "Name: ${it.name}") }
        CodecUtil.getAllSupportedCodecList().forEach { LogContext.log.i(TAG, "Name: ${it.name}") }

        val deviceInfo = DeviceUtil.getInstance(this).getDeviceInfo()
        binding.tv.text = deviceInfo
        LogContext.log.i(TAG, deviceInfo)

        DisplayCutoutManager.getInstance(this).getDisplayCutoutInfo { info ->
            LogContext.log.i(TAG, "Display cutout information: ${info.toJsonString()}")
            val cutoutRect = info.rects
            var cutoutStr = ""
            if (cutoutRect != null) {
                for ((i, rect) in cutoutRect.withIndex()) {
                    cutoutStr += "Display cutout[$i] in ${info.positions?.get(i)}   " +
                        "${rect.right - rect.left}x${rect.bottom - rect.top}\n"
                }
                binding.tv2.text = cutoutStr
            } else {
                binding.tv2.text = "Display cutout information: ${info.toJsonString()}"
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            for (index in 0 until DeviceUtil.getInstance(this@DeviceInfoActivity).cpuCoreCount) {
                val coreInfo = DeviceUtil.getInstance(this@DeviceInfoActivity).getCpuCoreInfoByIndex(index)
                LogContext.log.i(
                    TAG,
                    "cpu$index enable=${coreInfo?.online} minFreq=${coreInfo?.minFreq} maxFreq=${coreInfo?.maxFreq}"
                )
            }
        }

        val sb: StringBuilder = StringBuilder()
        sb.append("=====> AVC <===============================")
        sb.append("\n")
        LogContext.log.i(TAG, "=====> AVC <===============================")
        H264Util.getAvcCodec().forEach {
            LogContext.log.i(
                TAG,
                "AVC Encoder : ${it.name.padEnd(25)} isSoftwareCodec=${CodecUtil.isSoftwareCodec(it.name)}"
            )
            sb.append(it.name.padEnd(25))
            sb.append("\n")
        }
        H264Util.getAvcCodec(false).forEach {
            LogContext.log.i(
                TAG,
                "AVC Decoder : ${it.name.padEnd(25)} isSoftwareCodec=${CodecUtil.isSoftwareCodec(it.name)}"
            )
            sb.append(it.name.padEnd(25))
            sb.append("\n")
        }
        sb.append("=====> HEVC <==============================")
        sb.append("\n")
        LogContext.log.i(TAG, "=====> HEVC <==============================")
        H265Util.getHevcCodec().forEach {
            LogContext.log.i(
                TAG,
                "HEVC Encoder: ${it.name.padEnd(25)} isSoftwareCodec=${CodecUtil.isSoftwareCodec(it.name)}"
            )
            sb.append(it.name.padEnd(25))
            sb.append("\n")
        }
        H265Util.getHevcCodec(false).forEach {
            LogContext.log.i(
                TAG,
                "HEVC Decoder: ${it.name.padEnd(25)} isSoftwareCodec=${CodecUtil.isSoftwareCodec(it.name)}"
            )
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
