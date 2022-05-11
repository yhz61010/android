package com.leovp.leoandroidbaseutil.basic_components.examples.pref

import android.os.Bundle
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.log_sdk.base.ITAG

class PrefActivity : BaseDemonstrationActivity() {
    override fun getTagName(): String = ITAG

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pref)
    }
}