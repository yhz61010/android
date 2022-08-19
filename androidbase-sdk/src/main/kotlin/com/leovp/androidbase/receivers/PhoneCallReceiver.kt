package com.leovp.androidbase.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.leovp.log_sdk.LogContext
import java.util.*

/**
 * Author: Michael Leo
 * Date: 20-4-8 上午9:47
 *
 * @see [Check this](https://stackoverflow.com/a/15564021)
 *
 * Example:
 * ```kotlin
 * val callReceiver = object: PhoneCallReceiver() {
 *     override fun onIncomingCallReceived(ctx: Context, number: String?, start: Date?) { }
 *     override fun onIncomingCallAnswered(ctx: Context, number: String?, start: Date?) { }
 *     override fun onIncomingCallEnded(ctx: Context, number: String?, start: Date?, end: Date?) { }
 *     override fun onOutgoingCallStarted(ctx: Context, number: String?, start: Date?) { }
 *     override fun onOutgoingCallEnded(ctx: Context, number: String?, start: Date?, end: Date?) { }
 *     override fun onMissedCall(ctx: Context, number: String?, start: Date?) { }
 * }
 * val callIntentFilter = IntentFilter("android.intent.action.PHONE_STATE")
 * callIntentFilter.addAction(Intent.ACTION_NEW_OUTGOING_CALL)
 * registerReceiver(callReceiver, callIntentFilter)
 * ```
 */
abstract class PhoneCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) {
            LogContext.log.e(TAG, "onReceive intent or action is null")
            return
        }

        // We listen to two intents. The new outgoing call only tells us of an outgoing call. We use it to get the number.
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            savedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
        } else {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            var state = TelephonyManager.CALL_STATE_IDLE
            if (TelephonyManager.EXTRA_STATE_OFFHOOK == stateStr) {
                state = TelephonyManager.CALL_STATE_OFFHOOK
            } else if (TelephonyManager.EXTRA_STATE_RINGING == stateStr) {
                state = TelephonyManager.CALL_STATE_RINGING
            }
            onCallStateChanged(context, state, number)
        }
    }

    // Derived classes should override these to respond to specific events of interest
    protected abstract fun onIncomingCallReceived(ctx: Context, number: String?, start: Date?)
    protected abstract fun onIncomingCallAnswered(ctx: Context, number: String?, start: Date?)
    protected abstract fun onIncomingCallEnded(
        ctx: Context,
        number: String?,
        start: Date?,
        end: Date?
    )

    protected abstract fun onOutgoingCallStarted(ctx: Context, number: String?, start: Date?)
    protected abstract fun onOutgoingCallEnded(
        ctx: Context,
        number: String?,
        start: Date?,
        end: Date?
    )

    protected abstract fun onMissedCall(ctx: Context, number: String?, start: Date?)

    // Deals with actual events
    // Incoming call-  goes from IDLE to RINGING when it rings, to OFFHOOK when it's answered, to IDLE when its hung up
    // Outgoing call-  goes from IDLE to OFFHOOK when it dials out, to IDLE when hung up
    private fun onCallStateChanged(context: Context, state: Int, number: String?) {
        if (lastState == state) {
            // No change, debounce extras
            return
        }
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isIncoming = true
                callStartTime = Date()
                savedNumber = number
                onIncomingCallReceived(context, number, callStartTime)
            }
            // Transition of ringing->offhook are pickups of incoming calls.  Nothing done on them
            TelephonyManager.CALL_STATE_OFFHOOK ->
                if (lastState != TelephonyManager.CALL_STATE_RINGING) {
                    isIncoming = false
                    callStartTime = Date()
                    onOutgoingCallStarted(context, savedNumber, callStartTime)
                } else {
                    isIncoming = true
                    callStartTime = Date()
                    onIncomingCallAnswered(context, savedNumber, callStartTime)
                }
            // Went to idle - this is the end of a call.  What type depends on previous state(s)
            TelephonyManager.CALL_STATE_IDLE ->
                when {
                    lastState == TelephonyManager.CALL_STATE_RINGING -> {
                        // Ring but no pickup - a miss
                        onMissedCall(context, savedNumber, callStartTime)
                    }
                    isIncoming -> {
                        onIncomingCallEnded(context, savedNumber, callStartTime, Date())
                    }
                    else -> {
                        onOutgoingCallEnded(context, savedNumber, callStartTime, Date())
                    }
                }
        }
        lastState = state
    }

    companion object {
        private const val TAG = "PCR"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var callStartTime: Date? = null
        private var isIncoming = false
        private var savedNumber: String? =
            null // because the passed incoming is only valid in ringing
    }
}
