package com.leovp.demo.basiccomponents.examples.statusbar

import android.graphics.Color
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.leovp.android.exts.setOnSingleClickListener
import com.leovp.android.exts.statusBarColor
import com.leovp.android.exts.statusBarColorRes
import com.leovp.androidbase.exts.android.startActivity
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityStatusBarBinding
import com.leovp.log.base.ITAG

class StatusBarActivity : BaseDemonstrationActivity<ActivityStatusBarBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityStatusBarBinding {
        return ActivityStatusBarBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusBarColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))

        binding.btnColorInt.setOnSingleClickListener {
            statusBarColor(Color.MAGENTA)
        }

        binding.btnColorRes.setOnSingleClickListener {
            statusBarColorRes(android.R.color.holo_green_dark)
        }

        binding.btnImmersive.setOnSingleClickListener {
            startActivity(
                FullImmersiveActivity::class,
                { intent -> intent.putExtra("title", "Immersive Status bar") }
            )
        }
    }
}
