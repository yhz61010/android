package com.ho1ho.leoandroidbaseutil

import android.os.Bundle
import android.os.Environment
import com.ho1ho.androidbase.exts.ITAG
import com.ho1ho.androidbase.exts.toHexString
import com.ho1ho.androidbase.utils.LLog
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import com.ho1ho.mediacodec_sdk.h265.HEVCDecoder
import kotlinx.android.synthetic.main.activity_h265_decoder.*
import java.io.File
import java.io.FileInputStream

class H265DecoderActivity : BaseDemonstrationActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_h265_decoder)

        val h265File = File(Environment.getExternalStorageDirectory(), "h265.h265")
        val h265Is = FileInputStream(h265File)
        val fileData = ByteArray(200)
        h265Is.read(fileData, 0, 200)
        val decoder = HEVCDecoder()
        val csd0: ByteArray = decoder.getVpsSpsPps(fileData, 0, 100)!!
        LLog.w(ITAG, "csd0=${csd0.toHexString()}")
        decoder.initDecoder(surfaceView.holder.surface, csd0, 800, 1920)
    }
}