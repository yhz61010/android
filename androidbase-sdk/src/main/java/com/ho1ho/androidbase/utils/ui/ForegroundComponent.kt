package com.ho1ho.androidbase.utils.ui

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.os.Handler
import com.ho1ho.androidbase.utils.LLog
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
    @Suppress("WeakerAccess")
    var isForeground = false
        private set

    @Suppress("unused")
    val isBackground: Boolean
        get() = !isForeground

    private var paused = true
    private val handler = Handler()
    private val listeners: MutableList<AppStateListener> = CopyOnWriteArrayList()
    private var checkRunnable: Runnable? = null

    interface AppStateListener {
        fun onBecameForeground()
        fun onBecameBackground()
    }

    @Suppress("unused")
    fun addListener(listener: AppStateListener) {
        listeners.add(listener)
    }

    @Suppress("unused")
    fun removeListener(listener: AppStateListener) {
        listeners.remove(listener)
    }

    override fun onActivityResumed(activity: Activity) {
        paused = false
        val wasBackground = !isForeground
        isForeground = true
        checkRunnable?.let { handler.removeCallbacks(checkRunnable!!) }

        if (wasBackground) {
            LLog.i(TAG, "Went FG")
            listeners.forEach {
                try {
                    it.onBecameForeground()
                } catch (e: Exception) {
                    LLog.e(TAG, "onBecameForeground threw exception! msg=${e.message}")
                }
            }
        } else {
            LLog.i(TAG, "Still FG")
        }
    }

    override fun onActivityPaused(activity: Activity) {
        paused = true
        if (checkRunnable != null) {
            handler.removeCallbacks(checkRunnable!!)
        }
        handler.postDelayed(Runnable {
            if (isForeground && paused) {
                isForeground = false
                LLog.i(TAG, "Went BG")
                listeners.forEach {
                    try {
                        it.onBecameBackground()
                    } catch (e: Exception) {
                        LLog.e(TAG, "onBecameBackground threw exception! msg=${e.message}")
                    }
                }
            } else {
                LLog.i(TAG, "Still BG")
            }
        }.also { checkRunnable = it }, becameBackgroundDelay)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        try {
            LLog.i(TAG, "=====> onActivityDestroyed($activity) <=====")
        } catch (e: Exception) {
            LLog.e(TAG, "onActivityDestroyed error=${e.message}")
        }
    }

    companion object {
        private const val TAG = "FC"
        private const val CHECK_DELAY: Long = 500

        @Volatile
        private var instance: ForegroundComponent? = null

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
            if (instance == null) {
                synchronized(ForegroundComponent::class.java) {
                    if (instance == null) {
                        instance = ForegroundComponent(becameBackgroundDelay)
                        application.registerActivityLifecycleCallbacks(instance)
                    }
                }
            }
            return instance!!
        }

        fun get(): ForegroundComponent {
            checkNotNull(instance) { "ForegroundComponent is not initialised - invoke at least once with parameterised init/get" }
            return instance!!
        }
    }
}