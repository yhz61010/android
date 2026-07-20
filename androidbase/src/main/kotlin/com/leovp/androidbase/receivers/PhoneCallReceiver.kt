package com.leovp.androidbase.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.leovp.log.LogContext
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
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) {
            LogContext.log.e(TAG, "onReceive intent or action is null")
            return
        }

        // We listen to two intents. The new outgoing call only tells us of an outgoing call. We use
        // it to get the number.
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            synchronized(CallState) {
                CallState.savedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            }
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
    // Incoming calls move from IDLE to RINGING while ringing, then to OFFHOOK when answered,
    // and finally to IDLE when hung up.
    // Outgoing calls move from IDLE to OFFHOOK when dialing out, then to IDLE when hung up.
    private fun onCallStateChanged(context: Context, state: Int, number: String?) {
        // A device has a single telephony state, but PHONE_STATE broadcasts may be delivered to
        // freshly created receiver instances (manifest-registered) and possibly on different
        // threads. Guarding the whole transition on the shared [CallState] monitor keeps the FSM
        // consistent across instances and threads instead of relying on unsynchronized statics.
        synchronized(CallState) {
            if (CallState.lastState == state) {
                // No change, debounce extras
                return
            }
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    CallState.isIncoming = true
                    CallState.callStartTime = Date()
                    CallState.savedNumber = number
                    onIncomingCallReceived(context, number, CallState.callStartTime)
                }
                // Transitions from RINGING to OFFHOOK are pickups of incoming calls.
                TelephonyManager.CALL_STATE_OFFHOOK ->
                    if (CallState.lastState != TelephonyManager.CALL_STATE_RINGING) {
                        CallState.isIncoming = false
                        CallState.callStartTime = Date()
                        onOutgoingCallStarted(
                            context,
                            CallState.savedNumber,
                            CallState.callStartTime
                        )
                    } else {
                        CallState.isIncoming = true
                        CallState.callStartTime = Date()
                        onIncomingCallAnswered(
                            context,
                            CallState.savedNumber,
                            CallState.callStartTime
                        )
                    }
                // Went to idle - end of a call. What type depends on previous state(s).
                TelephonyManager.CALL_STATE_IDLE ->
                    when {
                        CallState.lastState == TelephonyManager.CALL_STATE_RINGING -> {
                            // Ring but no pickup - a miss
                            onMissedCall(context, CallState.savedNumber, CallState.callStartTime)
                        }
                        CallState.isIncoming -> {
                            onIncomingCallEnded(
                                context,
                                CallState.savedNumber,
                                CallState.callStartTime,
                                Date()
                            )
                        }
                        else -> {
                            onOutgoingCallEnded(
                                context,
                                CallState.savedNumber,
                                CallState.callStartTime,
                                Date()
                            )
                        }
                    }
            }
            CallState.lastState = state
        }
    }

    companion object {
        private const val TAG = "PCR"
    }

    /**
     * Thread-safe holder for the shared phone-call FSM state. Kept as a single process-wide
     * holder (rather than per-receiver-instance fields) so state survives across the separate
     * broadcasts that make up one call, and mutated only under `synchronized(CallState)`.
     */
    private object CallState {
        var lastState = TelephonyManager.CALL_STATE_IDLE
        var callStartTime: Date? = null
        var isIncoming = false

        // The passed incoming number is only valid while ringing, so it is cached here.
        var savedNumber: String? = null
    }
}
