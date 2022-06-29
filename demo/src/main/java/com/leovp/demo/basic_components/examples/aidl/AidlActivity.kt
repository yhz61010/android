package com.leovp.demo.basic_components.examples.aidl

import android.os.Bundle
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityAidlBinding
import com.leovp.log_sdk.base.ITAG

class AidlActivity : BaseDemonstrationActivity<ActivityAidlBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityAidlBinding {
        return ActivityAidlBinding.inflate(layoutInflater)
    }
}