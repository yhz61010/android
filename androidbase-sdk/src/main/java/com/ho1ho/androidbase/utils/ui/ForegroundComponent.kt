package com.ho1ho.androidbase.utils.ui

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.os.Handler
import com.ho1ho.androidbase.utils.CLog
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Author: Michael Leo
 * Date: 20-3-30 上午9:50
 */
/**
 * Usage:
 *
 * 1. Get the ForegroundComponent Singleton, passing a Context or Application object unless you
 * are sure that the Singleton has definitely already been initialised elsewhere.
 *
 * 2.a) Perform a direct, synchronous check: ForegroundComponent.isForeground() / .isBackground()
 *
 * or
 *
 * 2.b) Register to be notified (useful in Service or other non-UI components):
 *
 * ForegroundComponent.AppStateListener myListener = new ForegroundComponent.AppStateListener(){
 * public void onBecameForeground(){
 * // ... whatever you want to do
 * }
 * public void onBecameBackground(){
 * // ... whatever you want to do
 * }
 * }
 *
 * public void onCreate(){
 * super.onCreate();
 * ForegroundComponent.get(this).addListener(listener);
 * }
 *
 * public void onDestroy(){
 * super.onCreate();
 * ForegroundComponent.get(this).removeListener(listener);
 * }
 */
class ForegroundComponent(private var becameBackgroundDelay: Long = CHECK_DELAY) : ActivityLifecycleCallbacks {
    var isForeground = false
        private set

    @Suppress("unused")
    val isBackground: Boolean
        get() = !isForeground

    private var mPaused = true
    private val mHandler = Handler()
    private val mListeners: MutableList<AppStateListener> = CopyOnWriteArrayList()
    private var mCheckRunnable: Runnable? = null

    interface AppStateListener {
        fun onBecameForeground()
        fun onBecameBackground()
    }

    @Suppress("unused")
    fun addListener(listener: AppStateListener) {
        mListeners.add(listener)
    }

    @Suppress("unused")
    fun removeListener(listener: AppStateListener) {
        mListeners.remove(listener)
    }

    override fun onActivityResumed(activity: Activity) {
        mPaused = false
        val wasBackground = !isForeground
        isForeground = true
        mCheckRunnable?.let { mHandler.removeCallbacks(mCheckRunnable!!) }

        if (wasBackground) {
            CLog.i(
                TAG,
                "Went FG"
            )
            mListeners.forEach {
                try {
                    it.onBecameForeground()
                } catch (e: Exception) {
                    CLog.e(TAG, "onBecameForeground threw exception! msg=${e.message}")
                }
            }
        } else {
            CLog.i(TAG, "Still FG")
        }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        mPaused = true
        if (mCheckRunnable != null) {
            mHandler.removeCallbacks(mCheckRunnable!!)
        }
        mHandler.postDelayed(Runnable {
            if (isForeground && mPaused) {
                isForeground = false
                CLog.i(
                    TAG,
                    "Went BG"
                )
                mListeners.forEach {
                    try {
                        it.onBecameBackground()
                    } catch (e: Exception) {
                        CLog.e(
                            TAG,
                            "onBecameBackground threw exception! msg=${e.message}"
                        )
                    }
                }
            } else {
                CLog.i(
                    TAG,
                    "Still BG"
                )
            }
        }.also { mCheckRunnable = it }, becameBackgroundDelay)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        try {
            CLog.w(TAG, "=====> onActivityDestroyed($activity) <=====")
        } catch (e: Exception) {
            CLog.e(TAG, "onActivityDestroyed error=${e.message}")
        }
    }

    companion object {
        private const val TAG = "FC"
        private const val CHECK_DELAY: Long = 500

        @Volatile
        private var mInstance: ForegroundComponent? = null

        /**
         * Its not strictly necessary to use this method - _usually_ invoking
         * get with a Context gives us a path to retrieve the Application and
         * initialise, but sometimes (e.g. in test harness) the ApplicationContext
         * is != the Application, and the docs make no guarantees.
         *
         * @param application The application object
         * @return an initialised Foreground instance
         */
        fun init(application: Application, becameBackgroundDelay: Long = CHECK_DELAY): ForegroundComponent {
            if (mInstance == null) {
                synchronized(ForegroundComponent::class.java) {
                    if (mInstance == null) {
                        mInstance = ForegroundComponent(becameBackgroundDelay)
                        application.registerActivityLifecycleCallbacks(mInstance)
                    }
                }
            }
            return mInstance!!
        }

        fun get(): ForegroundComponent {
            checkNotNull(mInstance) { "ForegroundComponent is not initialised - invoke at least once with parameterised init/get" }
            return mInstance!!
        }
    }
}