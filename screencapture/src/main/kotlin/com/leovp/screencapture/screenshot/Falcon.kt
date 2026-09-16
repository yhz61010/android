package com.leovp.screencapture.screenshot

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.createBitmap
import com.leovp.log.LogContext
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Utility class to take screenshots of activity screen
 *
 * This class is copied mainly from
 * [jraska/Falcon](https://github.com/jraska/Falcon/blob/master/falcon/src/main/java/com/jraska/falc
 * on/Falcon.java).
 * Just adjust it to Kotlin
 */
object Falcon {
    //region Constants
    private const val TAG = "Falcon"

    /** Upper bound on waiting for the main thread to draw one screenshot. */
    private const val MAIN_THREAD_CAPTURE_TIMEOUT_MS = 1_000L

    //endregion
    //region Public API
    /**
     * Takes screenshot of provided activity and saves it to provided file.
     * File content will be overwritten if there is already some content.
     *
     * @param weakAct WeakReference<Activity> of which the screenshot will be taken.
     * @param toFile   File where the screenshot will be saved.
     * If there is some content it will be overwritten
     * @throws UnableToTakeScreenshotException When there is unexpected error during taking
     * screenshot
     */
    @Suppress("unused")
    fun takeScreenshot(weakAct: WeakReference<Activity>, toFile: File?) {
        requireNotNull(toFile) { "Parameter toFile cannot be null." }
        var bitmap: Bitmap? = null
        try {
            val captured = takeBitmapUnchecked(weakAct)
            bitmap = captured
            writeBitmap(captured, toFile)
        } catch (e: Exception) {
            val message =
                (
                    "Unable to take screenshot to file ${toFile.absolutePath} of activity " +
                        "${weakAct.javaClass.name}"
                    )
            LogContext.log.e(TAG, message, e)
            throw UnableToTakeScreenshotException(message, e)
        } finally {
            bitmap?.recycle()
        }
        LogContext.log.d(TAG, "Screenshot captured to " + toFile.absolutePath)
    }

    /**
     * Takes screenshot of provided activity and puts it into bitmap.
     *
     * @param activity WeakReference<Activity> of which the screenshot will be taken.
     * @return Bitmap of what is displayed in activity.
     */
    fun takeScreenshotBitmap(
        activity: WeakReference<Activity>,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    ): Bitmap? = try {
        takeBitmapUnchecked(activity, config)
    } catch (e: Exception) {
        val message = ("Unable to take screenshot to bitmap of activity " + activity.javaClass.name)
        LogContext.log.e(TAG, message, e)
        //            throw UnableToTakeScreenshotException(message, e)
        null
    }

    //endregion
    //region Methods
    @Throws(InterruptedException::class)
    private fun takeBitmapUnchecked(
        weakAct: WeakReference<Activity>,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    ): Bitmap = if (Looper.myLooper() == Looper.getMainLooper()) {
        captureOnMainThread(weakAct, config)
    } else {
        captureFromOtherThread(weakAct, config)
    }

    /**
     * Collects the window list, sizes the bitmap and draws, all in one main-thread turn.
     *
     * The window list and its layout params live in two `WindowManagerGlobal` lists that are
     * mutated together under its own lock. Reading them from another thread can observe the two
     * out of step, which misaligns a root with its layout params or throws while indexing. Doing
     * the whole capture on the main thread removes that window, and makes the attach check
     * before drawing reliable rather than a best guess made earlier on another thread.
     */
    private fun captureOnMainThread(
        weakAct: WeakReference<Activity>,
        config: Bitmap.Config,
    ): Bitmap {
        val viewRoots = getRootViews(weakAct)
        if (viewRoots.isEmpty()) {
            throw UnableToTakeScreenshotException("Unable to capture any view data in $weakAct")
        }
        var maxWidth = Int.MIN_VALUE
        var maxHeight = Int.MIN_VALUE
        for (viewRoot in viewRoots) {
            if (viewRoot.winFrame.right > maxWidth) {
                maxWidth = viewRoot.winFrame.right
            }
            if (viewRoot.winFrame.bottom > maxHeight) {
                maxHeight = viewRoot.winFrame.bottom
            }
        }
        val bitmap = createBitmap(maxWidth, maxHeight, config)
        drawRootsToBitmap(viewRoots, bitmap)
        return bitmap
    }

    /**
     * Hands the capture to the main thread and waits for it, bounded.
     *
     * The bitmap is created by the main-thread turn, so a caller that gives up on the timeout
     * never holds a bitmap that the main thread may still be drawing into.
     */
    @Throws(InterruptedException::class)
    private fun captureFromOtherThread(
        weakAct: WeakReference<Activity>,
        config: Bitmap.Config,
    ): Bitmap {
        val activity = weakAct.get()
            ?: throw UnableToTakeScreenshotException("Activity is gone before taking screenshot")
        val captured = AtomicReference<Bitmap>()
        val errorInMainThread = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        activity.runOnUiThread {
            try {
                captured.set(captureOnMainThread(weakAct, config))
            } catch (e: Throwable) {
                errorInMainThread.set(e)
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(MAIN_THREAD_CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            // Giving up does not stop the main-thread turn: it still runs and still produces a
            // full-screen bitmap nobody will claim. Queue the recycle behind it, on the same
            // looper, so a busy main thread does not leave one such bitmap per timed-out frame.
            activity.runOnUiThread { captured.getAndSet(null)?.recycle() }
            throw UnableToTakeScreenshotException(
                "Main thread did not draw the screenshot within ${MAIN_THREAD_CAPTURE_TIMEOUT_MS}ms"
            )
        }
        errorInMainThread.get()?.let { throw UnableToTakeScreenshotException(it) }
        return captured.get()
            ?: throw UnableToTakeScreenshotException("Screenshot was not produced")
    }

    private fun drawRootsToBitmap(viewRoots: List<ViewRootData>, bitmap: Bitmap) {
        for (rootData in viewRoots) {
            // One window that refuses to draw must not cost the whole frame. The remaining
            // windows still produce a usable screenshot.
            try {
                drawRootToBitmap(rootData, bitmap)
            } catch (e: Exception) {
                LogContext.log.e(TAG, "Skip window that failed to draw", e)
            }
        }
    }

    private fun drawRootToBitmap(config: ViewRootData, bitmap: Bitmap) {
        // Roots are collected on the caller's thread and drawn later on the main thread. A
        // window can be added or removed in between, and drawing a view whose AttachInfo is
        // already gone throws inside View.onDrawScrollIndicators(). Re-check here, where the
        // answer is reliable because this runs on the main thread.
        if (!config.view.isAttachedToWindow) {
            LogContext.log.w(TAG, "Skip detached window while taking screenshot")
            return
        }
        // now only dim supported
        if (config.layoutParams.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND
            == WindowManager.LayoutParams.FLAG_DIM_BEHIND
        ) {
            val dimCanvas = Canvas(bitmap)
            val alpha = (255 * config.layoutParams.dimAmount).toInt()
            dimCanvas.drawARGB(alpha, 0, 0, 0)
        }
        val canvas = Canvas(bitmap)
        canvas.translate(config.winFrame.left.toFloat(), config.winFrame.top.toFloat())
        config.view.draw(canvas)
    }

    @Throws(IOException::class)
    private fun writeBitmap(bitmap: Bitmap, toFile: File) {
        var outputStream: OutputStream? = null
        try {
            outputStream = BufferedOutputStream(FileOutputStream(toFile))
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        } finally {
            closeQuietly(outputStream)
        }
    }

    private fun closeQuietly(closable: Closeable?) {
        if (closable != null) {
            runCatching { closable.close() }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    @Suppress("UNCHECKED_CAST")
    fun getRootViews(weakAct: WeakReference<Activity>): List<ViewRootData> {
        // val globalWindowManager: Any = if (Build.VERSION.SDK_INT <=
        // Build.VERSION_CODES.JELLY_BEAN) {
        //     getFieldValue("mWindowManager", weakAct.get()?.windowManager)!!
        // } else {
        //     getFieldValue("mGlobal", weakAct.get()?.windowManager)!!
        // }

        // Since minSdk is 21, Build.VERSION_CODES.JELLY_BEAN is always below minSdk.
        // We can safely use "mGlobal" which exists in API 21+.
        val windowManager = weakAct.get()?.windowManager
            ?: throw UnableToTakeScreenshotException("Activity is gone before taking screenshot")
        val globalWindowManager: Any = checkNotNull(getFieldValue("mGlobal", windowManager)) {
            "WindowManagerGlobal.mGlobal is not reachable on API ${Build.VERSION.SDK_INT}. " +
                "It may be blocked by the non-SDK interface restrictions."
        }

        // These two are the fields most likely to be blocked, so they carry the same diagnosis as
        // mGlobal above. Without it a blocked lookup surfaces as a bare cast NullPointerException
        // and the log says only that the screenshot failed.
        val rootObjects = getFieldValue("mRoots", globalWindowManager)
        val paramsObject = getFieldValue("mParams", globalWindowManager)
        val roots: Array<Any> = reflectedList<Any>("mRoots", rootObjects).toTypedArray()
        val params: Array<WindowManager.LayoutParams> =
            reflectedList<WindowManager.LayoutParams>("mParams", paramsObject).toTypedArray()

        val rootViews = viewRootData(roots, params)
        if (rootViews.isEmpty()) {
            return emptyList()
        }
        offsetRootsTopLeft(rootViews)
        ensureDialogsAreAfterItsParentActivities(rootViews)
        return rootViews
    }

    private fun viewRootData(
        roots: Array<Any>,
        params: Array<WindowManager.LayoutParams>
    ): MutableList<ViewRootData> {
        val rootViews: MutableList<ViewRootData> = ArrayList()
        for (i in roots.indices) {
            val root = roots[i]
            val rootView = getFieldValue("mView", root) as? View

            // fixes https://github.com/jraska/Falcon/issues/10
            if (rootView == null) {
                LogContext.log.i(TAG, "null View stored as root in Global window manager, skipping")
                continue
            }
            if (!rootView.isShown) {
                continue
            }
            val location = IntArray(2)
            rootView.getLocationOnScreen(location)
            val left = location[0]
            val top = location[1]
            val area = Rect(left, top, left + rootView.width, top + rootView.height)
            // The root list and the params list are snapshotted separately, so a window removed
            // in between leaves this index without params. Skip it instead of throwing.
            val rootParams = params.getOrNull(i) ?: continue
            rootViews.add(ViewRootData(rootView, area, rootParams))
        }
        return rootViews
    }

    private fun offsetRootsTopLeft(rootViews: List<ViewRootData>) {
        var minTop = Int.MAX_VALUE
        var minLeft = Int.MAX_VALUE
        for (rootView in rootViews) {
            if (rootView.winFrame.top < minTop) {
                minTop = rootView.winFrame.top
            }
            if (rootView.winFrame.left < minLeft) {
                minLeft = rootView.winFrame.left
            }
        }
        for (rootView in rootViews) {
            rootView.winFrame.offset(-minLeft, -minTop)
        }
    }

    // This fixes issue #11. It is not perfect solution and maybe there is another case
    // of different type of view, but it works for most common case of dialogs.
    private fun ensureDialogsAreAfterItsParentActivities(viewRoots: MutableList<ViewRootData>) {
        if (viewRoots.size <= 1) {
            return
        }
        for (dialogIndex in 0 until viewRoots.size - 1) {
            val viewRoot = viewRoots[dialogIndex]
            if (!viewRoot.isDialogType) {
                continue
            }
            if (viewRoot.windowToken == null) {
                // make sure we will never compare null == null
                return
            }
            for (parentIndex in dialogIndex + 1 until viewRoots.size) {
                val possibleParent = viewRoots[parentIndex]
                if (possibleParent.isActivityType &&
                    possibleParent.windowToken === viewRoot.windowToken
                ) {
                    viewRoots.remove(possibleParent)
                    viewRoots.add(dialogIndex, possibleParent)
                    break
                }
            }
        }
    }

    /**
     * Reads a reflected `WindowManagerGlobal` list, naming the field when it cannot be read.
     *
     * A blocked lookup yields null, and casting that straight to a non-null type would raise a
     * [NullPointerException] that says nothing about why. Callers see the field name and the
     * likely cause instead.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> reflectedList(fieldName: String, value: Any?): List<T> =
        (value as? List<T>) ?: throw UnableToTakeScreenshotException(
            "WindowManagerGlobal.$fieldName is not reachable on API ${Build.VERSION.SDK_INT}. " +
                "It may be blocked by the non-SDK interface restrictions."
        )

    private fun getFieldValue(fieldName: String, target: Any?): Any? = runCatching {
        getFieldValueUnchecked(fieldName, target)
    }.getOrNull()

    private fun getFieldValueUnchecked(fieldName: String, target: Any?): Any? = runCatching {
        findField(fieldName, target?.javaClass)?.let {
            it.isAccessible = true
            it[target]
        }
    }.getOrNull()

    /**
     * Caches the fields this class reflects on. The names looked up here are fixed framework
     * fields, so a hit is valid for the life of the process.
     *
     * Every capture walks these lookups, and capturing now runs on the main thread: without the
     * cache each frame would call [Class.getDeclaredFields], which allocates a fresh array and a
     * fresh [Field] for every declared field of classes as large as `ViewRootImpl` — tens of
     * thousands of short-lived objects per second on the UI thread.
     */
    private val fieldCache = ConcurrentHashMap<String, Field>()

    private fun findField(name: String, clazz: Class<*>?): Field? {
        if (clazz == null) return null
        val cacheKey = "${clazz.name}#$name"
        fieldCache[cacheKey]?.let { return it }
        return findFieldUncached(name, clazz)?.also { fieldCache[cacheKey] = it }
    }

    private fun findFieldUncached(name: String, clazz: Class<*>?): Field? {
        var currentClass: Class<*>? = clazz
        // The null check is part of the loop condition rather than left to a NullPointerException
        // caught further down: a class with no superclass ends the walk, it is not an error.
        while (currentClass != null && currentClass != Any::class.java) {
            val inspected: Class<*> = currentClass
            val declaredFields = runCatching { inspected.declaredFields }.getOrElse { return null }
            declaredFields.firstOrNull { name == it.name }?.let { return it }
            currentClass = runCatching { inspected.superclass }.getOrElse { return null }
        }
        LogContext.log.e(TAG, "Field $name not found for class $clazz")
        return null
    }

    //endregion
    //region Nested classes
    /**
     * Custom exception thrown if there is some exception thrown during
     * screenshot capturing to enable better client code exception handling.
     */
    class UnableToTakeScreenshotException : RuntimeException {
        internal constructor(detailMessage: String) : super(detailMessage)
        internal constructor(detailMessage: String, exception: Throwable) : super(
            detailMessage,
            extractException(exception)
        )

        constructor(ex: Throwable) : super(extractException(ex))

        companion object {
            /**
             * Method to avoid multiple wrapping. If there is already our exception,
             * just wrap the cause again
             */
            private fun extractException(ex: Throwable): Throwable? =
                if (ex is UnableToTakeScreenshotException) {
                    ex.cause
                } else {
                    ex
                }
        }
    }

    class ViewRootData(
        val view: View,
        val winFrame: Rect,
        val layoutParams: WindowManager.LayoutParams
    ) {
        val isDialogType: Boolean
            get() = layoutParams.type == WindowManager.LayoutParams.TYPE_APPLICATION

        val isActivityType: Boolean
            get() = layoutParams.type == WindowManager.LayoutParams.TYPE_BASE_APPLICATION

        val windowToken: IBinder?
            get() = layoutParams.token

        fun context(): Context = view.context
    } //endregion
}
