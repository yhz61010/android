package com.leovp.leoandroidbaseutil.basic_components.examples.animation

import android.os.Bundle
import com.leovp.leoandroidbaseutil.base.BaseDemonstrationActivity
import com.leovp.leoandroidbaseutil.databinding.ActivityViewAnimBinding

class ViewAnimActivity : BaseDemonstrationActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityViewAnimBinding.inflate(layoutInflater).apply { setContentView(root) }

        title = "View Animation"
    }
}