package com.leovp.drawonscreen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import com.leovp.drawOnScreen.sdk.R
import kotlin.math.abs

/**
 * The component is inheriting [ImageView], so the the `android:src` attribute is mandatory.
 * The default `android:src` is a transparent 1x1 Pixel png.
 * The default `android:scaleType` is `centerCrop`
 *
 * Author: Michael Leo
 * Date: 2020/11/02 19:10
 */
class FingerPaintView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) :
    ImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val DEF_EDIT_MODE = true
    }

    private enum class BrushType {
        BLUR, /*EMBOSS,*/ NORMAL
    }

    var touchEventCallback: TouchEventCallback? = null

    private val defaultStrokeColor = Color.RED
    private val defaultStrokeWidth = 12f
    private val defaultTouchTolerance = 4f
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
        BlurMaskFilter(5F, BlurMaskFilter.Blur.NORMAL)
    }

    var strokeColor = defaultStrokeColor
        set(value) {
            field = value
            pathPaint.color = value
        }

    var strokeWidth = defaultStrokeWidth
        set(value) {
            field = value
            pathPaint.strokeWidth = value
        }

    private val matrixValues = FloatArray(9)
        get() = field.apply { imageMatrix.getValues(this) }

    var touchTolerance = defaultTouchTolerance

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
            context.theme.obtainStyledAttributes(it, R.styleable.FingerPaintImageView, defStyleAttr, defStyleRes).run {
                runCatching {
                    strokeColor = getColor(R.styleable.FingerPaintImageView_strokeColor, defaultStrokeColor)
                    strokeWidth = getDimension(R.styleable.FingerPaintImageView_strokeWidth, defaultStrokeWidth)
                    inEditMode = getBoolean(R.styleable.FingerPaintImageView_inEditMode, DEF_EDIT_MODE)
                    touchTolerance = getFloat(R.styleable.FingerPaintImageView_touchTolerance, defaultTouchTolerance)
                }.also { recycle() }
            }
        }
        if (drawable == null) setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.onebyone, null))
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    /**
     * Get current screen's width and height
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        brushBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { brushCanvas = Canvas(it) }
    }

    /**
     * If there are any paths drawn on top of the image, this will return a bitmap with the original
     * content plus the drawings on top of it. Otherwise, the original bitmap will be returned.
     */
    override fun getDrawable(): Drawable? {
        return super.getDrawable()?.let {
            if (!isModified()) return it

            val inverse = Matrix().apply { imageMatrix.invert(this) }
            val scale = FloatArray(9).apply { inverse.getValues(this) }[Matrix.MSCALE_X]

            runCatching {
                // draw original bitmap
                val result = Bitmap.createBitmap(it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                it.draw(canvas)

                val transformedPath = Path()
                val transformedPaint = Paint()
                // Call requires API level 24 (current min is 21): java.lang.Iterable#forEach [NewApi]
//                paths.forEach { (path, paint) ->
//                    path.transform(inverse, transformedPath)
//                    transformedPaint.set(paint)
//                    transformedPaint.strokeWidth *= scale
//                    canvas.drawPath(transformedPath, transformedPaint)
//                }
                for ((path, paint) in paths) {
                    path.transform(inverse, transformedPath)
                    transformedPaint.set(paint)
                    transformedPaint.strokeWidth *= scale
                    canvas.drawPath(transformedPath, transformedPaint)
                }
                BitmapDrawable(resources, result)
            }.getOrNull()
        }
    }

    private fun getCurrentPath() = paths.lastOrNull()?.first

    @SuppressLint("ClickableViewAccessibility")
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
                    countDrawn++
                    invalidate()
                    touchEventCallback?.onTouchUp(event.x, event.y, pathPaint)
                }
            }
        }
        return inEditMode
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
            paths.add(Path().also { it.moveTo(event.x, event.y) } to Paint(pathPaint))
            currentX = event.x
            currentY = event.y
        }
    }

    private fun handleTouchMove(event: MotionEvent) {
        val sourceBitmap = super.getDrawable() ?: return

        val xTranslation = matrixValues[Matrix.MTRANS_X]
        val yTranslation = matrixValues[Matrix.MTRANS_Y]
        val scale = matrixValues[Matrix.MSCALE_X]

        val xPos = event.x.coerceIn(xTranslation, xTranslation + sourceBitmap.intrinsicWidth * scale)
        val yPos = event.y.coerceIn(yTranslation, yTranslation + sourceBitmap.intrinsicHeight * scale)

        val dx = abs(xPos - currentX)
        val dy = abs(yPos - currentY)

        if (dx >= touchTolerance || dy >= touchTolerance) {
            getCurrentPath()?.quadTo(currentX, currentY, (xPos + currentX) / 2, (yPos + currentY) / 2)
            currentX = xPos
            currentY = yPos
        }
    }

    private fun handleTouchEnd() = getCurrentPath()?.lineTo(currentX, currentY)

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
        canvas.save()
        runCatching {
            for (index in paths.indices) {
                val path = paths[index]
                if (index >= countDrawn) {
                    path.second.maskFilter =
                        when (currentBrush) {
//                        BrushType.EMBOSS -> defaultEmboss
                            BrushType.BLUR -> defaultBlur
                            BrushType.NORMAL -> null
                        }
                }
                brushCanvas?.drawPath(paths[index].first, paths[index].second)
            }
            brushBitmap?.let { canvas.drawBitmap(it, 0f, 0f, defaultBitmapPaint) }
        }.onFailure { /* You can ignore this error. */ }
        canvas.restore()
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
    fun undo() {
        touchEventCallback?.onUndo()
        paths.takeIf { it.isNotEmpty() }?.removeAt(paths.lastIndex)
        countDrawn--
        invalidate()
    }

    /**
     * Returns true if any paths are currently drawn on the image, false otherwise.
     */
    @Suppress("WeakerAccess", "SENSELESS_COMPARISON")
    fun isModified(): Boolean {
        // Although the paths field is non-nullable, when initializing the view for the first time,
        // this value will be nullable in a very short time then change to non-nullable.
        return if (paths != null) {
            paths.isNotEmpty()
        } else {
            false
        }
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
    fun drawUserPath(userPath: MutableList<Pair<Path, Paint>>) {
        clear()
        paths.addAll(0, userPath)
        invalidate()
    }

    fun getPaths() = paths.toMutableList()
}
