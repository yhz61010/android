package com.leovp.androidbase.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Author: Michael Leo
 * Date: 19-11-11 下午1:45
 *
 * Example:
 * ```kotlin:
 * val receiverTime = TimeChangeReceiver()
 * receiverTime.setOnUpdateUIListener(object : TimeChangeReceiver.UpdateUIListener {
 *     override fun update(str: String) {
 *     }
 * })
 * // This action will be called every minute by system.
 * val intentFilter = IntentFilter(Intent.ACTION_TIME_TICK)
 * registerReceiver(receiverTime, intentFilter)
 * ```
 */
class TimeChangeReceiver : BroadcastReceiver() {
    private var updateUIListener: UpdateUIListener? = null

    interface UpdateUIListener {
        fun update(str: String)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_TIME_TICK == intent.action) {
            updateUIListener?.update(Intent.ACTION_TIME_TICK)
        }
    }

    fun setOnUpdateUIListener(updateUIListener: UpdateUIListener) {
        this.updateUIListener = updateUIListener
    }
}
