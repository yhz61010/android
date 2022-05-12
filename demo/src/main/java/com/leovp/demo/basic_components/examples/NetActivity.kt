package com.leovp.demo.basic_components.examples

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityNetBinding
import com.leovp.lib_common_android.exts.setOnSingleClickListener
import com.leovp.log_sdk.base.ITAG

class NetActivity : BaseDemonstrationActivity() {
    override fun getTagName(): String = ITAG

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_net)
        val binding = ActivityNetBinding.inflate(layoutInflater).apply { setContentView(root) }

        binding.tvInfo1.setOnSingleClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/liangjingkanji/Net")))
        }
        binding.tvInfo2.setOnSingleClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://liangjingkanji.github.io/Net/")))
        }
    }
}