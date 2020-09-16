package com.ho1ho.leoandroidbaseutil.basic_components.examples

import android.media.AudioFormat
import android.os.Bundle
import android.widget.Toast
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.audiorecord.MicRecorder
import com.ho1ho.audiorecord.bean.AudioCodecInfo
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.runtime.Permission
import kotlinx.android.synthetic.main.activity_audio.*

class AudioActivity : BaseDemonstrationActivity() {
    private lateinit var micRecorder: MicRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio)

        val audioCodec = AudioCodecInfo(16000, 32000, AudioFormat.CHANNEL_IN_MONO, 1, AudioFormat.ENCODING_PCM_16BIT)
        micRecorder = MicRecorder(audioCodec)

        btnRecordPcm.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AndPermission.with(this)
                    .runtime()
                    .permission(Permission.RECORD_AUDIO)
                    .onGranted {
                        micRecorder.startRecord { pcmData ->
                            LLog.d(ITAG, "PCM data[${pcmData.size}]")
                        }
                    }
                    .onDenied { Toast.makeText(this, "Deny record permission", Toast.LENGTH_SHORT).show();finish() }
                    .start()
            } else {
                micRecorder.stopRecord()
            }
        }
    }

    override fun onStop() {
        micRecorder.stopRecord()
        super.onStop()
    }
}