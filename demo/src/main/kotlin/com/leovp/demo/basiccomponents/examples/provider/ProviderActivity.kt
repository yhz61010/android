package com.leovp.demo.basiccomponents.examples.provider

import android.os.Bundle
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityProviderBinding
import com.leovp.log.base.ITAG

class ProviderActivity : BaseDemonstrationActivity<ActivityProviderBinding>(R.layout.activity_provider) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityProviderBinding {
        return ActivityProviderBinding.inflate(layoutInflater)
    }
}
