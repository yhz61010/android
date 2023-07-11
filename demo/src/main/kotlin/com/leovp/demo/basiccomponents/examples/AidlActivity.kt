package com.leovp.demo.basiccomponents.examples

import android.os.Bundle
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityAidlBinding
import com.leovp.log.base.ITAG

class AidlActivity : BaseDemonstrationActivity<ActivityAidlBinding>(R.layout.activity_aidl) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityAidlBinding {
        return ActivityAidlBinding.inflate(layoutInflater)
    }
}
