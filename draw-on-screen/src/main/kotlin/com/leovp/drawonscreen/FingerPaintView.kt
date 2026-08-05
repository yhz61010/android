package com.leovp.drawonscreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import kotlin.math.abs

/**
 * The component inherits from [ImageView], so the `android:src` attribute is mandatory.
 * The default `android:src` is a transparent 1x1 pixel PNG.
 * The default `android:scaleType` is `centerCrop`
 *
 * Author: Michael Leo
 * Date: 2020/11/02 19:10
 */
class FingerPaintView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val DEF_EDIT_MODE = true

        private const val DEFAULT_STROKE_COLOR = Color.RED
        private const val DEFAULT_STROKE_WIDTH = 12f
        private const val DEFAULT_TOUCH_TOLERANCE = 4f

        private const val DEFAULT_BLUR_RADIUS = 5F
        private const val MATRIX_SIZE = 9
    }

    private enum class BrushType {
        BLUR,

        /*EMBOSS,*/
        NORMAL
    }

    var touchEventCallback: TouchEventCallback? = null
    private val defaultBitmapPaint = Paint(Paint.DITHER_FLAG)
    private var brushBitmap: Bitmap? = null
    private var brushCanvas: Canvas? = null
    private var countDrawn = 0
    private var currentBrush = BrushType.NORMAL

    @Suppress("WeakerAccess")
    var inEditMode = DEF_EDIT_MODE

    /**
     * According to document, `EmbossMaskFilter` is deprecated.
     *
     * > This subclass is not supported and should not be instantiated.
     */
    //    private val defaultEmboss: EmbossMaskFilter by lazy {
    //        EmbossMaskFilter(floatArrayOf(1F, 1F, 1F), 0.4F, 6F, 3.5F)
    //    }
    private val defaultBlur: BlurMaskFilter by lazy {
        BlurMaskFilter(DEFAULT_BLUR_RADIUS, BlurMaskFilter.Blur.NORMAL)
    }

    var strokeColor = DEFAULT_STROKE_COLOR
        set(value) {
            field = value
            pathPaint.color = value
        }

    var strokeWidth = DEFAULT_STROKE_WIDTH
        set(value) {
            field = value
            pathPaint.strokeWidth = value
        }

    private val matrixValues = FloatArray(MATRIX_SIZE)
        get() = field.apply { imageMatrix.getValues(this) }

    var touchTolerance = DEFAULT_TOUCH_TOLERANCE

    private val pathPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        color = strokeColor
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@FingerPaintView.strokeWidth
    }

    private var currentX = 0f
    private var currentY = 0f
    private var paths: MutableList<Pair<Path, Paint>> = mutableListOf()

    init {
        attrs?.let {
            context.theme.obtainStyledAttributes(
                it,
                R.styleable.FingerPaintImageView,
                defStyleAttr,
                defStyleRes
            ).run {
                runCatching {
                    strokeColor = getColor(
                        R.styleable.FingerPaintImageView_strokeColor,
                        DEFAULT_STROKE_COLOR
                    )
                    strokeWidth = getDimension(
                        R.styleable.FingerPaintImageView_strokeWidth,
                        DEFAULT_STROKE_WIDTH
                    )
                    inEditMode = getBoolean(
                        R.styleable.FingerPaintImageView_inEditMode,
                        DEF_EDIT_MODE
                    )
                    touchTolerance = getFloat(
                        R.styleable.FingerPaintImageView_touchTolerance,
                        DEFAULT_TOUCH_TOLERANCE
                    )
                }.onFailure { err ->
                    // Do not silently skip bad styled attributes; surface via android.util.Log
                    // without adding a project log dependency (remediation M-D1).
                    Log.e("FingerPaintView", "read styled attributes failed", err)
                }.also { recycle() }
            }
        }
        if (drawable == null) {
            setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.onebyone,
                    null
                )
            )
        }
        scaleType = ScaleType.CENTER_CROP
    }

    /**
     * Get current screen's width and height
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Bitmap.createBitmap throws for a non-positive size; skip until the view has real bounds
        // (remediation M-D3).
        if (w <= 0 || h <= 0) return
        // Recycle the previous buffer before allocating a new one to avoid a native-memory leak on
        // repeated size changes (remediation M-D2).
        brushBitmap?.recycle()
        brushBitmap = createBitmap(w, h).also { brushCanvas = Canvas(it) }
    }

    /**
     * If there are any paths drawn on top of the image, this will return a bitmap with the original
     * content plus the drawings on top of it. Otherwise, the original bitmap will be returned.
     */
    override fun getDrawable(): Drawable? {
        return super.getDrawable()?.let {
            if (!isModified()) return it

            val inverse = Matrix().apply { imageMatrix.invert(this) }
            val scale = FloatArray(MATRIX_SIZE).apply { inverse.getValues(this) }[Matrix.MSCALE_X]

            runCatching {
                // draw original bitmap
                val result = createBitmap(it.intrinsicWidth, it.intrinsicHeight)
                val canvas = Canvas(result)
                it.draw(canvas)

                val transformedPath = Path()
                val transformedPaint = Paint()
                // Call requires API level 24 (current min is 21): java.lang.Iterable#forEach
                // [NewApi]
                // paths.forEach { (path, paint) ->
                //     path.transform(inverse, transformedPath)
                //     transformedPaint.set(paint)
                //     transformedPaint.strokeWidth *= scale
                //     canvas.drawPath(transformedPath, transformedPaint)
                // }
                for ((path, paint) in paths) {
                    path.transform(inverse, transformedPath)
                    transformedPaint.set(paint)
                    transformedPaint.strokeWidth *= scale
                    canvas.drawPath(transformedPath, transformedPaint)
                }
                result.toDrawable(resources)
            }.getOrNull()
        }
    }

    private fun getCurrentPath() = synchronized(this) {
        paths.takeIf { it.size > countDrawn }?.lastOrNull()?.first
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (inEditMode) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handleTouchStart(event)
                    invalidate()
                    touchEventCallback?.onTouchDown(event.x, event.y, pathPaint)
                }

                MotionEvent.ACTION_MOVE -> {
                    handleTouchMove(event)
                    invalidate()
                    touchEventCallback?.onTouchMove(event.x, event.y, pathPaint)
                }

                MotionEvent.ACTION_UP -> {
                    handleTouchEnd()
                    invalidate()
                    touchEventCallback?.onTouchUp(event.x, event.y, pathPaint)
                    // Route the lift as an accessibility click so TalkBack and any registered
                    // OnClickListener are notified, instead of suppressing the lint (remediation L-D2).
                    performClick()
                }

                MotionEvent.ACTION_CANCEL -> {
                    // Finalize the in-progress stroke on gesture cancellation so no partial path is
                    // left dangling; no onTouchUp callback is fired for a cancel (remediation M-D6).
                    handleTouchEnd()
                    invalidate()
                }
            }
        }
        return inEditMode
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleTouchStart(event: MotionEvent) {
        val sourceBitmap = super.getDrawable() ?: return

        val xTranslation = matrixValues[Matrix.MTRANS_X]
        val yTranslation = matrixValues[Matrix.MTRANS_Y]
        val scale = matrixValues[Matrix.MSCALE_X]

        val imageBounds = RectF(
            xTranslation,
            yTranslation,
            xTranslation + sourceBitmap.intrinsicWidth * scale,
            yTranslation + sourceBitmap.intrinsicHeight * scale
        )

        // make sure drawings are kept within the image bounds
        if (imageBounds.contains(event.x, event.y)) {
            // Structural mutation shares the monitor used by onDraw's snapshot and the other
            // mutators (remediation H9).
            synchronized(this) {
                countDrawn = countDrawn.coerceIn(0, paths.size)
                paths.add(Path().also { it.moveTo(event.x, event.y) } to Paint(pathPaint))
            }
            currentX = event.x
            currentY = event.y
        }
    }

    private fun handleTouchMove(event: MotionEvent) {
        val sourceBitmap = super.getDrawable() ?: return

        val xTranslation = matrixValues[Matrix.MTRANS_X]
        val yTranslation = matrixValues[Matrix.MTRANS_Y]
        val scale = matrixValues[Matrix.MSCALE_X]

        val xPos = event.x.coerceIn(
            xTranslation,
            xTranslation + sourceBitmap.intrinsicWidth * scale
        )
        val yPos = event.y.coerceIn(
            yTranslation,
            yTranslation + sourceBitmap.intrinsicHeight * scale
        )

        val dx = abs(xPos - currentX)
        val dy = abs(yPos - currentY)

        if (dx >= touchTolerance || dy >= touchTolerance) {
            getCurrentPath()?.quadTo(
                currentX,
                currentY,
                (xPos + currentX) / 2,
                (yPos + currentY) / 2
            )
            currentX = xPos
            currentY = yPos
        }
    }

    private fun handleTouchEnd() {
        synchronized(this) {
            val currentPath = paths.takeIf { it.size > countDrawn }?.lastOrNull()?.first ?: return
            currentPath.lineTo(currentX, currentY)
            countDrawn = (countDrawn + 1).coerceAtMost(paths.size)
        }
    }

    interface TouchEventCallback {
        fun onTouchDown(x: Float, y: Float, paint: Paint)
        fun onTouchMove(x: Float, y: Float, paint: Paint)
        fun onTouchUp(x: Float, y: Float, paint: Paint)
        fun onClear()
        fun onUndo()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        brushBitmap?.eraseColor(Color.TRANSPARENT)
        brushCanvas?.drawColor(Color.TRANSPARENT)
        try {
            // Render over an immutable snapshot taken under the same monitor the structural mutators
            // (undo/clear/drawUserPath/handleTouchStart) hold, so a concurrent change cannot skew the
            // indices or raise ConcurrentModificationException mid-draw. The heavy drawing then runs
            // outside the lock (remediation H9).
            val snapshot = synchronized(this) { paths.toList() }
            for (index in snapshot.indices) {
                val path = snapshot[index]
                if (index >= countDrawn) {
                    path.second.maskFilter =
                        when (currentBrush) {
                            //                        BrushType.EMBOSS -> defaultEmboss
                            BrushType.BLUR -> defaultBlur
                            BrushType.NORMAL -> null
                        }
                }
                brushCanvas?.drawPath(path.first, path.second)
            }
            brushBitmap?.let { canvas.drawBitmap(it, 0f, 0f, defaultBitmapPaint) }
        } catch (e: Exception) {
            // Do not swallow silently; log so render failures are diagnosable. Errors (e.g. OOM)
            // are intentionally NOT caught here (remediation H8).
            Log.e("FingerPaintView", "onDraw failed", e)
        }
    }

    /**
     * Enable normal mode
     */
    fun normal() {
        currentBrush = BrushType.NORMAL
    }

    /**
     * Change brush type to emboss
     *
     * According to document, `EmbossMaskFilter` is deprecated.
     *
     * > This subclass is not supported and should not be instantiated.
     */
    //    fun emboss() {
    //        currentBrush = BrushType.EMBOSS
    //    }

    /**
     * Change brush type to blur
     */
    fun blur() {
        currentBrush = BrushType.BLUR
    }

    /**
     * Removes the last full path from the view.
     */
    @Synchronized
    fun undo() {
        touchEventCallback?.onUndo()
        // Only decrement the counter when a path was actually removed, and never let it go negative;
        // an unconditional `countDrawn--` on an empty list underflowed the counter (remediation H7).
        if (paths.isNotEmpty()) {
            paths.removeAt(paths.lastIndex)
            countDrawn = countDrawn.coerceAtMost(paths.size).coerceAtLeast(0)
        }
        invalidate()
    }

    /**
     * Returns true if any paths are currently drawn on the image, false otherwise.
     */
    @Suppress("WeakerAccess", "SENSELESS_COMPARISON")
    fun isModified(): Boolean {
        // Although the paths field is non-nullable, when initializing the view for the first time,
        // this value will be nullable in a very short time then change to non-nullable.
        return paths.isNotEmpty()
    }

    /**
     * Clears all existing paths from the image.
     */
    @Synchronized
    fun clear() {
        touchEventCallback?.onClear()
        paths.clear()
        countDrawn = 0
        invalidate()
    }

    @Synchronized
    fun drawUserPath(userPath: List<Pair<Path, Paint>>) {
        clear()
        paths.addAll(0, userPath)
        countDrawn = paths.size
        invalidate()
    }

    @Synchronized
    fun getPaths(): List<Pair<Path, Paint>> = paths.toList()
}
