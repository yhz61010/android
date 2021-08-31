package com.leovp.androidbase.exts.android.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.leovp.androidbase.utils.system.API


/**
 * @author Sukcria Miksria
 * @version 2019/03/12
 */
@RequiresApi(API.L)
class SystemUiUtil(private val window: Window) {

    constructor(fragment: Fragment) : this(fragment.requireActivity())

    constructor(activity: Activity) : this(activity.window)

    private val decor: View = window.decorView

    /**
     * Color shade(0~255)
     * grayscale <= 178 深色背景(使用浅色图标)
     * grayscale > 178 浅色背景(使用深色图标)
     */
    var grayscale = 178

    /**
     * View.SYSTEM_UI_FLAG_VISIBLE <=> 默认标记
     * View.SYSTEM_UI_FLAG_LOW_PROFILE <=> 低调模式, 会隐藏不重要的状态栏图标
     * View.SYSTEM_UI_FLAG_LAYOUT_STABLE <=> 保持整个View稳定, 常与 控制System UI悬浮 or 隐藏的Flags共用, 使View不会因为System UI的变化而重新layout
     * View.SYSTEM_UI_FLAG_FULLSCREEN <=> 隐藏状态栏
     * View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN <=> 视图延伸至状态栏区域，状态栏上浮于视图之上
     * View.SYSTEM_UI_FLAG_HIDE_NAVIGATION <=> 隐藏导航栏
     * View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION <=> 视图延伸至导航栏区域，导航栏上浮于视图之上
     * View.SYSTEM_UI_FLAG_IMMERSIVE <=> 沉浸模式, 隐藏状态栏和导航栏, 并且在第一次会弹泡提醒, 并且在状态栏区域滑动可以呼出状态栏
     * 使之生效，需要和View.SYSTEM_UI_FLAG_FULLSCREEN，View.SYSTEM_UI_FLAG_HIDE_NAVIGATION中的一个或两个同时设置
     * （这样会系统会清除之前设置的View.SYSTEM_UI_FLAG_FULLSCREEN或View.SYSTEM_UI_FLAG_HIDE_NAVIGATION标志）
     * View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY <=> 与SYSTEM_UI_FLAG_IMMERSIVE的区别是
     * 呼出隐藏的状态栏后不会清除之前设置的View.SYSTEM_UI_FLAG_FULLSCREEN或View.SYSTEM_UI_FLAG_HIDE_NAVIGATION标志
     * 在一段时间后将再次隐藏系统栏
     */
    private var flags
        get() = decor.systemUiVisibility
        set(value) {
            decor.systemUiVisibility = value
        }


    /**
     * 设置虚拟键和状态栏颜色&主题
     */
    fun setSystemBar(color: Int): SystemUiUtil {
        setStatusBar(color)
        setNavigationBar(color)
        return this
    }

    /**
     * 设置虚拟键和状态栏主题
     */
    @SuppressLint("NewApi")
    fun setSystemBarStyle(color: Int): SystemUiUtil {
        if (API.ABOVE_M) setStatusBarStyle(color)
        if (API.ABOVE_O) setNavigationBarStyle(color)
        return this
    }


    /**
     * 设置状态栏颜色&主题
     * @param color 颜色
     */
    @SuppressLint("NewApi")
    fun setStatusBar(color: Int): SystemUiUtil {
        window.statusBarColor = when {
            API.ABOVE_M -> color.apply { setStatusBarStyle(this) }
            ColorUtil(color).grayscale < this.grayscale -> color
            else -> Color.BLACK
        }
        return this
    }

    /**
     * 设置状态栏主题
     */
    @RequiresApi(API.M)
    fun setStatusBarStyle(color: Int): SystemUiUtil {
        val status = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        flags = if (ColorUtil(color).grayscale > grayscale) {
            flags or status
        } else {
            flags and status.inv()
        }
        return this
    }


    /**
     * 设置虚拟键颜色&主题
     * @param color 颜色
     */
    @SuppressLint("NewApi")
    fun setNavigationBar(color: Int): SystemUiUtil {
        window.navigationBarColor = when {
            API.ABOVE_O -> color.apply { setNavigationBarStyle(this) }
            ColorUtil(color).grayscale < this.grayscale -> color
            else -> Color.BLACK
        }
        return this
    }

    /**
     * 设置虚拟键主题
     */
    @RequiresApi(API.O)
    fun setNavigationBarStyle(color: Int): SystemUiUtil {
        val navigation = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        flags = if (ColorUtil(color).grayscale > grayscale) {
            flags or navigation
        } else {
            flags and navigation.inv()
        }
        return this
    }


    /**
     * 隐藏系统栏
     * Hide System Bar
     */
    fun hideSystemBar(status: Boolean, navigation: Boolean, stick: Boolean = true, stable: Boolean = true): SystemUiUtil {
        val sf = View.SYSTEM_UI_FLAG_FULLSCREEN
        val nf = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        val f1 = View.SYSTEM_UI_FLAG_IMMERSIVE
        val f2 = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val st = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        flags = if (navigation) flags or nf else flags and nf.inv()
        flags = if (status) flags or sf else flags and sf.inv()
        if(stable) flags or st else flags and st.inv()
        flags = if (stick) flags or f2 and f1.inv() else flags or f1 and f2.inv()
        return this
    }

    /**
     * 透明化系统栏
     * Translucent system bars
     */
    fun translucentSystemBar(switch: Boolean): SystemUiUtil {
        val sf = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val nf = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        flags = if (switch) flags or sf or nf else flags and (sf or nf).inv()
        return this
    }

    /**
     * 透明化状态栏
     * Translucent status bars
     */
    fun translucentStatusBar(switch: Boolean): SystemUiUtil {
        val sf = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val nf = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        flags = if (switch) flags or sf and nf.inv() else flags and (sf or nf).inv()
        return this
    }

}