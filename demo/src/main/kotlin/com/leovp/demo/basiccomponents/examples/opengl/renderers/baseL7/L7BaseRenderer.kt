package com.leovp.demo.basiccomponents.examples.opengl.renderers.baseL7

import android.content.Context
import com.leovp.opengl.BaseRenderer

/**
 * Author: Michael Leo
 * Date: 2022/4/12 10:14
 */
abstract class L7BaseRenderer(@Suppress("unused") private val ctx: Context) : BaseRenderer() {
    var isCurrentFrameRead = false
}
