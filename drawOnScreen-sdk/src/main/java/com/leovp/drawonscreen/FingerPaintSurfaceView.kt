package com.leovp.drawonscreen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View

/**
 * Author: Michael Leo
 * Date: 20-11-5 上午9:46
 */
class FingerPaintSurfaceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : SurfaceView(
    context,
    attrs,
    defStyle
), SurfaceHolder.Callback, View.OnTouchListener {
    init {
        initView()
    }

    private var countDrawn = 0
    private val paint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 14F
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val path = Path()

    private fun initView() {
        isFocusable = true
        isFocusableInTouchMode = true
        holder.addCallback(this)
        setOnTouchListener(this)
    }

    private fun draw() {
        var canvas: Canvas? = null
        runCatching {
            canvas = holder.lockCanvas()?.apply {
                drawColor(Color.TRANSPARENT)
                drawPath(path, paint)
            }
        }.onFailure { it.printStackTrace() }.also {
            canvas?.let { holder.unlockCanvasAndPost(it) }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        draw()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(event.x, event.y)
                draw()
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(event.x, event.y)
                draw()
            }
            MotionEvent.ACTION_UP -> {
                countDrawn++
            }
        }
        return true
    }

    fun undo() {
//        paths.takeIf { it.isNotEmpty() }?.removeAt(paths.lastIndex)
        countDrawn--
//        invalidate()
    }

    fun clear() {
        path.reset()
        countDrawn = 0
        draw()
    }
}