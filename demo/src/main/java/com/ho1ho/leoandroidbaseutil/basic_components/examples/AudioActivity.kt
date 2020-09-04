package com.ho1ho.leoandroidbaseutil.basic_components.examples

import android.media.AudioFormat
import android.os.Bundle
import com.ho1ho.audiorecord.MicRecord
import com.ho1ho.leoandroidbaseutil.R
import com.ho1ho.leoandroidbaseutil.base.BaseDemonstrationActivity

class AudioActivity : BaseDemonstrationActivity() {
    private lateinit var micRecord: MicRecord

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio)

        micRecord = MicRecord(16000, 32000, 1, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        micRecord.doRecord(object : MicRecord.Callback {
            override fun onCallback(data: ByteArray?) {
//                CLog.d(ITAG, "doRecord onCallback=${data?.size}")
            }
        })
    }

    override fun onDestroy() {
        micRecord.release()
        super.onDestroy()
    }
}