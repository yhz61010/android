package com.ho1ho.leoandroidbaseutil

import android.os.Bundle
import android.os.Environment
import com.ho1ho.leoandroidbaseutil.ui.base.BaseDemonstrationActivity
import java.io.File
import java.io.FileInputStream

class H265DecoderActivity : BaseDemonstrationActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_h265_decoder)

        val h265File = File(Environment.getExternalStorageDirectory(), "h265.h265")
        val h265Is = FileInputStream(h265File)
    }
}