package com.leovp.leoandroidbaseutil.basic_components.examples.opengl.renderers.base_L7

import android.content.Context
import com.leovp.opengl_sdk.BaseRenderer

/**
 * Author: Michael Leo
 * Date: 2022/4/12 10:14
 */
abstract class L7_BaseRenderer(@Suppress("unused") private val ctx: Context) : BaseRenderer() {
    var isCurrentFrameRead = false
}