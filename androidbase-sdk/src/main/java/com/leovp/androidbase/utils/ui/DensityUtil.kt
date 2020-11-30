package com.leovp.androidbase.utils.ui

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.View
import com.leovp.androidbase.annotations.Density
import com.leovp.androidbase.exts.android.app
import com.leovp.androidbase.exts.android.isLandscape
import com.leovp.androidbase.exts.android.isPortrait
import com.leovp.androidbase.exts.android.utils.DimenUtil

object DensityUtil : ComponentCallbacks {

    /** Screen scale */
    var scale = 0f
    private var density: Float = 0f
    private var scaledDensity: Float = 0f
    private lateinit var displayMetrics: DisplayMetrics

    /**
     * Initialize DisplayMetrics in `Application`
     *
     * This method should be call in `Application`
     */
    fun init() {
        displayMetrics = app.resources.displayMetrics
        // Check whether to initialize
        if (density != 0f) return
        // Initialize
        density = displayMetrics.density
        scaledDensity = displayMetrics.scaledDensity
        // Monitor the changing of font size
        app.registerComponentCallbacks(this)
    }

    /**
     * Initialize density in `Activity`
     *
     * @param ruler Designed UI width. Default value: 420dp
     */
    fun init(activity: Activity, ruler: Float = 420f, @Density flag: Int = Density.SHORT_SIDE_BASED) {
        val f = activity.window.decorView.systemUiVisibility
        val sf1 = View.SYSTEM_UI_FLAG_FULLSCREEN
        val sf2 = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val nf = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        val sf = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        val navigation = ((f and sf) == sf) || ((f and nf) == nf)
        val status = ((f and sf) == sf) || ((f and sf1) == sf1) || ((f and sf2) == sf2)
        init(activity, ruler, status, navigation, flag)
    }

    /**
     * Initialize density in `Activity`
     *
     * @param ruler Designed UI width. Default value: 420dp
     * @param status Whether the designed dimension contains the Action Bar(Title Bar)
     * @param navigation Whether the designed dimension contains the Navigation Bar
     */
    fun init(activity: Activity, ruler: Float = 420f, status: Boolean = false, navigation: Boolean = false, @Density flag: Int = Density.SHORT_SIDE_BASED) {
        val dimen = DimenUtil(activity)
        val width = dimen.width(navigation)
        val height = dimen.height(status, navigation)
        val pixels = when (flag) {
            Density.WIDTH_BASED -> width
            Density.HEIGHT_BASED -> height
            Density.SHORT_SIDE_BASED -> if (app.isPortrait) width else height
            Density.LONG_SIDE_BASED -> if (app.isLandscape) width else height
            else -> 0
        }
        init(activity.resources.displayMetrics, pixels / ruler)
    }

    /**
     * Initialize density in `Activity`
     *
     * @param portRuler Designed UI width. Default value: 420dp
     * @param landRuler Designed UI height. Default value: 980dp
     */
    fun init(activity: Activity, portRuler: Float = 420f, landRuler: Float) {
        val f = activity.window.decorView.systemUiVisibility
        val nf = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        val sf = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        val navigation = ((f and sf) == sf) || ((f and nf) == nf)
        init(activity, portRuler, landRuler, navigation)
    }

    /**
     * Initialize density in `Activity`
     *
     * @param portRuler Designed UI width. Default value: 420dp
     * @param landRuler Designed UI height. Default value: 980dp
     * @param navigation Whether the designed dimension contains the Navigation Bar
     */
    fun init(activity: Activity, portRuler: Float = 420f, landRuler: Float = 980f, navigation: Boolean = false) {
        val pixels = DimenUtil(activity).width(navigation)
        val ruler = if (app.isLandscape) landRuler else portRuler
        init(activity.resources.displayMetrics, pixels / ruler)
    }

    /**
     * Initialize density in `Activity`
     */
    private fun init(metrics: DisplayMetrics, density: Float) {
        metrics.density = density
        metrics.densityDpi = (160 * density).toInt()
        metrics.scaledDensity = density * (scaledDensity / DensityUtil.density)
        scale = DensityUtil.density / density

        setBitmapDensity(metrics.densityDpi)
    }

    /**
     * 设置 Bitmap 的默认屏幕密度
     * 由于 Bitmap 的屏幕密度是读取配置的，需要使用反射强行修改
     * @param density 屏幕密度
     */
    private fun setBitmapDensity(density: Int) {
        try {
            val cls = Class.forName("android.graphics.Bitmap")
            val field = cls.getDeclaredField("sDefaultDensity")
            field.isAccessible = true
            field.set(null, density)
            field.isAccessible = false
        } catch (e: ClassNotFoundException) {
        } catch (e: NoSuchFieldException) {
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
    }

    override fun onLowMemory() {}

    /** Font changed callback */
    override fun onConfigurationChanged(newConfig: Configuration) {
        if (newConfig.fontScale > 0) scaledDensity = displayMetrics.scaledDensity
    }

}