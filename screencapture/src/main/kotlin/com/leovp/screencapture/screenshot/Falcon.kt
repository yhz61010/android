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
import com.leovp.log.LogContext
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * Utility class to take screenshots of activity screen
 *
 * This class is copied mainly from [jraska/Falcon](https://github.com/jraska/Falcon/blob/master/falcon/src/main/java/com/jraska/falcon/Falcon.java).
 * Just adjust it to Kotlin
 */
object Falcon {
    //region Constants
    private const val TAG = "Falcon"

    //endregion
    //region Public API
    /**
     * Takes screenshot of provided activity and saves it to provided file.
     * File content will be overwritten if there is already some content.
     *
     * @param weakAct WeakReference<Activity> of which the screenshot will be taken.
     * @param toFile   File where the screenshot will be saved.
     * If there is some content it will be overwritten
     * @throws UnableToTakeScreenshotException When there is unexpected error during taking screenshot
     */
    @Suppress("unused")
    fun takeScreenshot(weakAct: WeakReference<Activity>, toFile: File?) {
        requireNotNull(toFile) { "Parameter toFile cannot be null." }
        var bitmap: Bitmap? = null
        try {
            bitmap = takeBitmapUnchecked(weakAct)
            writeBitmap(bitmap, toFile)
        } catch (e: Exception) {
            val message =
                ("Unable to take screenshot to file ${toFile.absolutePath} of activity ${weakAct.javaClass.name}")
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
        val bitmap = Bitmap.createBitmap(maxWidth, maxHeight, config)

        // We need to do it in main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            drawRootsToBitmap(viewRoots, bitmap)
        } else {
            drawRootsToBitmapOtherThread(weakAct, viewRoots, bitmap)
        }
        return bitmap
    }

    @Throws(InterruptedException::class)
    private fun drawRootsToBitmapOtherThread(
        weakAct: WeakReference<Activity>,
        viewRoots: List<ViewRootData>,
        bitmap: Bitmap,
    ) {
        val errorInMainThread = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        weakAct.get()?.runOnUiThread {
            runCatching { drawRootsToBitmap(viewRoots, bitmap) }.getOrElse { errorInMainThread.set(it) }
                .also { latch.countDown() }
        } ?: latch.countDown()
        latch.await()
        errorInMainThread.get()?.let {
            throw UnableToTakeScreenshotException(it)
        }
    }

    private fun drawRootsToBitmap(viewRoots: List<ViewRootData>, bitmap: Bitmap) {
        for (rootData in viewRoots) {
            drawRootToBitmap(rootData, bitmap)
        }
    }

    private fun drawRootToBitmap(config: ViewRootData, bitmap: Bitmap) {
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
    private fun writeBitmap(bitmap: Bitmap?, toFile: File) {
        var outputStream: OutputStream? = null
        try {
            outputStream = BufferedOutputStream(FileOutputStream(toFile))
            bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
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
        val globalWindowManager: Any = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
            getFieldValue("mWindowManager", weakAct.get()?.windowManager)!!
        } else {
            getFieldValue("mGlobal", weakAct.get()?.windowManager)!!
        }
        val rootObjects = getFieldValue("mRoots", globalWindowManager)
        val paramsObject = getFieldValue("mParams", globalWindowManager)
        val params: Array<WindowManager.LayoutParams>

        //  There was a change to ArrayList implementation in 4.4
        val roots: Array<Any> = (rootObjects as List<Any>).toTypedArray()
        val paramsList = paramsObject as List<WindowManager.LayoutParams>
        params = paramsList.toTypedArray()

        val rootViews = viewRootData(roots, params)
        if (rootViews.isEmpty()) {
            return emptyList()
        }
        offsetRootsTopLeft(rootViews)
        ensureDialogsAreAfterItsParentActivities(rootViews)
        return rootViews
    }

    private fun viewRootData(roots: Array<Any>, params: Array<WindowManager.LayoutParams>): MutableList<ViewRootData> {
        val rootViews: MutableList<ViewRootData> = ArrayList()
        for (i in roots.indices) {
            val root = roots[i]
            val rootView = getFieldValue("mView", root) as? View

            // fixes https://github.com/jraska/Falcon/issues/10
            if (rootView == null) {
                LogContext.log.e(TAG, "null View stored as root in Global window manager, skipping")
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
            rootViews.add(ViewRootData(rootView, area, params[i]))
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
                if (possibleParent.isActivityType && possibleParent.windowToken === viewRoot.windowToken) {
                    viewRoots.remove(possibleParent)
                    viewRoots.add(dialogIndex, possibleParent)
                    break
                }
            }
        }
    }

    private fun getFieldValue(fieldName: String, target: Any?): Any? = runCatching {
        getFieldValueUnchecked(fieldName, target)
    }.getOrNull()

    private fun getFieldValueUnchecked(fieldName: String, target: Any?): Any? = runCatching {
        findField(fieldName, target?.javaClass)?.let {
            it.isAccessible = true
            it[target]
        }
    }.getOrNull()

    private fun findField(name: String, clazz: Class<*>?): Field? {
        var currentClass: Class<*>? = clazz
        while (currentClass != Any::class.java) {
            runCatching {
                val refCurrentClass: Class<*> = currentClass!!
                for (field in refCurrentClass.declaredFields) {
                    if (name == field.name) {
                        return field
                    }
                }
                currentClass = refCurrentClass.superclass
            }.onFailure { return null }
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
            private fun extractException(ex: Throwable): Throwable? = if (ex is UnableToTakeScreenshotException) {
                ex.cause
            } else {
                ex
            }
        }
    }

    class ViewRootData(val view: View, val winFrame: Rect, val layoutParams: WindowManager.LayoutParams) {
        val isDialogType: Boolean
            get() = layoutParams.type == WindowManager.LayoutParams.TYPE_APPLICATION

        val isActivityType: Boolean
            get() = layoutParams.type == WindowManager.LayoutParams.TYPE_BASE_APPLICATION

        val windowToken: IBinder?
            get() = layoutParams.token

        fun context(): Context = view.context
    } //endregion
}
