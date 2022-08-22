package com.leovp.demo.basiccomponents.examples.pref

import android.os.Bundle
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityPrefBinding
import com.leovp.log.base.ITAG

class PrefActivity : BaseDemonstrationActivity<ActivityPrefBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityPrefBinding {
        return ActivityPrefBinding.inflate(layoutInflater)
    }
}
