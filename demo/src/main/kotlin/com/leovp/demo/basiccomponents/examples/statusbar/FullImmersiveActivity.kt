package com.leovp.demo.basiccomponents.examples.statusbar

import android.graphics.Color
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.leovp.android.exts.immersive
import com.leovp.android.exts.immersiveExit
import com.leovp.android.exts.setActionBarTransparent
import com.leovp.android.exts.setOnSingleClickListener
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityFullImmersiveBinding
import com.leovp.log.base.ITAG

class FullImmersiveActivity : BaseDemonstrationActivity<ActivityFullImmersiveBinding>() {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityFullImmersiveBinding {
        return ActivityFullImmersiveBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // For immersive status bar with image just needs 2 steps:
        // 1. Set `windowActionBarOverlay` in your theme.
        // 2. Set actionbar with transparent.
        // 3. immersive status bar with transparent.
        setActionBarTransparent()
        immersive()

        binding.btnSetColorByView.setOnSingleClickListener {
            // window.clearFlags(Window.FEATURE_ACTION_BAR_OVERLAY)
            // setActionBarBackgroundRes(R.color.purple_500)
            immersive(binding.tvBaseColor)
        }

        binding.btnMagentaLight.setOnSingleClickListener {
            // window.clearFlags(Window.FEATURE_ACTION_BAR_OVERLAY)
            // setActionBarBackgroundRes(R.color.purple_500)
            immersive(Color.MAGENTA, false)
        }

        binding.btnMagentaDark.setOnSingleClickListener {
            // window.clearFlags(Window.FEATURE_ACTION_BAR_OVERLAY)
            // setActionBarBackgroundRes(R.color.purple_500)
            immersive(Color.MAGENTA, true)
        }

        binding.btnTranslucent.setOnSingleClickListener {
            immersive(ContextCompat.getColor(this, R.color.purple_700_translucent), false)
        }

        binding.btnRestore.setOnSingleClickListener {
            immersiveExit()
        }
    }
}
