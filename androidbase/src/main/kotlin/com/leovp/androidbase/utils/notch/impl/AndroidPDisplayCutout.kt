package com.leovp.androidbase.utils.notch.impl

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.leovp.androidbase.utils.notch.DisplayCutout

@RequiresApi(Build.VERSION_CODES.P)
internal class AndroidPDisplayCutout : DisplayCutout {
    /** Always return _true_. However, you should check notch by the result of [cutoutAreaRect] method. */
    override fun supportDisplayCutout(activity: Activity): Boolean = true

    /** Note that, calling this method will cause status bar being hidden. */
    override fun fillDisplayCutout(activity: Activity) {
        val window = activity.window
        val lp = window.attributes
        // By using this flag, the window can be displayed in a way that avoids overlapping with the cutout area,
        // providing a more immersive and visually pleasing experience.
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = lp

        // Allow content to extend into status bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Hide status bar
        WindowCompat.getInsetsController(window, window.decorView).run {
            hide(WindowInsetsCompat.Type.statusBars())
            // hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun cutoutAreaRect(activity: Activity, callback: DisplayCutout.CutoutAreaRectCallback) {
        val contentView = activity.window.decorView
        contentView.post(
            Runnable {
                val windowInsets = contentView.rootWindowInsets
                if (windowInsets != null) {
                    val cutout = windowInsets.displayCutout
                    if (cutout != null) {
                        callback.onResult(cutout.boundingRects)
                        return@Runnable
                    }
                }
                callback.onResult(null)
            }
        )
    }
}
