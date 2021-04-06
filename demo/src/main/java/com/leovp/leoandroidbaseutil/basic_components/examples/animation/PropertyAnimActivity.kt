package com.leovp.leoandroidbaseutil.basic_components.examples.animation

import android.os.Bundle
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity

class PropertyAnimActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_property_anim)
        title = "Property Animation"
    }
}