package com.leovp.leoandroidbaseutil.basic_components.examples

import android.os.Bundle
import com.leovp.androidbase.exts.android.getRealResolution
import com.leovp.androidbase.exts.android.utils.DeviceUtil
import com.leovp.androidbase.exts.kotlin.toJsonString
import com.leovp.androidbase.utils.log.LogContext
import com.leovp.androidbase.utils.media.CodecUtil
import com.leovp.androidbase.utils.notch.INotchScreen
import com.leovp.androidbase.utils.notch.NotchScreenManager
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityDeviceInfoBinding

class DeviceInfoActivity : BaseDemonstrationActivity() {

    private lateinit var binding: ActivityDeviceInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceInfoBinding.inflate(layoutInflater).apply { setContentView(root) }

        //        CodecUtil.getEncoderListByMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC).forEach { LogContext.log.i(TAG, "Name: ${it.name}") }
        CodecUtil.getAllSupportedCodecList().forEach { LogContext.log.i(TAG, "Name: ${it.name}") }

        val deviceInfo = DeviceUtil.getDeviceInfo(this)
        binding.tv.text = deviceInfo
        LogContext.log.i(TAG, deviceInfo)

        NotchScreenManager.getNotchInfo(this, object : INotchScreen.NotchScreenCallback {
            override fun onResult(notchScreenInfo: INotchScreen.NotchScreenInfo) {
                LogContext.log.i(TAG, "notchScreenInfo: ${notchScreenInfo.toJsonString()}")
                notchScreenInfo.notchRects?.let {
                    val halfScreenWidth = getRealResolution().x / 2
                    if (it[0].left < halfScreenWidth && halfScreenWidth < it[0].right) {
                        LogContext.log.i(TAG, "Notch in Middle")
                    } else if (halfScreenWidth < it[0].left) {
                        LogContext.log.i(TAG, "Notch in Right")
                    } else {
                        LogContext.log.i(TAG, "Notch in Left")
                    }
                }
            }
        })
    }

    companion object {
        private const val TAG = "DeviceInfo"
    }
}