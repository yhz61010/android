package com.ho1ho.leoandroidbaseutil.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Author: Michael Leo
 * Date: 20-6-17 上午11:14
 */
open class BaseDemonstrationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = intent.getStringExtra("title")
    }
}