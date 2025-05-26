package com.leovp.demo.basiccomponents.examples.statusbar

import android.graphics.Color
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.leovp.android.exts.actionBarHeight
import com.leovp.android.exts.addStatusBarMargin
import com.leovp.android.exts.getDimenInPixel
import com.leovp.android.exts.immersive
import com.leovp.android.exts.immersiveExit
import com.leovp.android.exts.setActionBarTransparent
import com.leovp.android.exts.setOnSingleClickListener
import com.leovp.android.exts.topMargin
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityFullImmersiveBinding
import com.leovp.log.base.ITAG

class FullImmersiveActivity :
    BaseDemonstrationActivity<ActivityFullImmersiveBinding>(R.layout.activity_full_immersive) {
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

        resetProperMargin()

        binding.btnSetColorByView.setOnSingleClickListener {
            // window.clearFlags(Window.FEATURE_ACTION_BAR_OVERLAY)
            // setActionBarBackgroundRes(R.color.purple_500)
            immersive(binding.tvBaseColor)
            resetProperMargin()
        }

        binding.btnMagentaLight.setOnSingleClickListener {
            // window.clearFlags(Window.FEATURE_ACTION_BAR_OVERLAY)
            // setActionBarBackgroundRes(R.color.purple_500)
            immersive(Color.MAGENTA, false)
            resetProperMargin()
        }

        binding.btnMagentaDark.setOnSingleClickListener {
            // window.clearFlags(Window.FEATURE_ACTION_BAR_OVERLAY)
            // setActionBarBackgroundRes(R.color.purple_500)
            immersive(Color.MAGENTA, true)
            resetProperMargin()
        }

        binding.btnTranslucent.setOnSingleClickListener {
            immersive(ContextCompat.getColor(this, R.color.purple_700_translucent), false)
            resetProperMargin()
        }

        binding.btnRestoreWithDark.setOnSingleClickListener {
            immersiveExit(true)
            restoreMargin()
        }

        binding.btnRestoreWithLight.setOnSingleClickListener {
            immersiveExit(false)
            restoreMargin()
        }

        binding.btnRestore.setOnSingleClickListener {
            immersiveExit()
            restoreMargin()
        }
    }

    private fun restoreMargin() {
        binding.tvBaseColor.topMargin = getDimenInPixel("default_margin")
        binding.tvBaseColor.topMargin += actionBarHeight
    }

    private fun resetProperMargin() {
        restoreMargin()
        binding.tvBaseColor.addStatusBarMargin()
    }
}
