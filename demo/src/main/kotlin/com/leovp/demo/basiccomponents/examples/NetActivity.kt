package com.leovp.demo.basiccomponents.examples

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.leovp.android.exts.setOnSingleClickListener
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityNetBinding
import com.leovp.log.base.ITAG

class NetActivity : BaseDemonstrationActivity<ActivityNetBinding>(R.layout.activity_net) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityNetBinding =
        ActivityNetBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.tvInfo1.setOnSingleClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/liangjingkanji/Net")
                )
            )
        }
        binding.tvInfo2.setOnSingleClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://liangjingkanji.github.io/Net/")
                )
            )
        }
    }
}
