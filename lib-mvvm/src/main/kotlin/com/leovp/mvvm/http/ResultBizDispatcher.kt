@file:Suppress("unused", "LongParameterList")

package com.leovp.mvvm.http

import com.leovp.log.base.d
import com.leovp.mvvm.event.base.UiEvent
import com.leovp.mvvm.event.base.UiEventManager
import com.leovp.network.http.ResultBiz
import com.leovp.network.http.exception
import com.leovp.network.http.exception.ResultException
import com.leovp.network.http.exception.business.BusinessException
import com.leovp.network.http.exception.business.ReloginException
import com.leovp.network.http.get
import com.leovp.network.http.getBizErrData
import com.leovp.network.http.getReloginErrData

/**
 * Author: Michael Leo
 * Date: 2025/9/29 15:51
 */

private const val TAG = "ResultBiz"

/**
 * Dispatches a [ResultBiz] to the appropriate callback based on its type.
 *
 * This function handles four possible outcomes of a business result:
 * - [ResultBiz.Success] — invokes [onSuccess] with the result data and extra data.
 * - [ResultBiz.BusinessError] — invokes [onBizError] with the exception and error data.
 * - [ResultBiz.Failure] — invokes [onFailure] if provided, otherwise sends a toast event
 *   via [uiEventManager].
 * - [ResultBiz.Relogin] — invokes [onRelogin] if provided, otherwise attempts to send
 *   a relogin event via [uiEventManager].
 *
 * @param R the base result type.
 * @param T the concrete result type, a subtype of [R].
 * @param uiEventManager optional [UiEventManager] used to send UI events (e.g., toast, relogin)
 *   when no explicit callback is provided.
 * @param bizResult the [ResultBiz] to dispatch.
 * @param onSuccess called when [bizResult] is [ResultBiz.Success], receiving the data and extra data.
 * @param onBizError called when [bizResult] is [ResultBiz.BusinessError], receiving the exception
 *   and the associated error data.
 * @param onFailure optional callback for [ResultBiz.Failure]. If `null`, a toast is shown instead.
 * @param onRelogin optional callback for [ResultBiz.Relogin]. If `null`, a relogin event is sent instead.
 * @param onLast optional callback invoked in the `finally` block for [ResultBiz.Success],
 *   [ResultBiz.BusinessError], and [ResultBiz.Failure] cases, typically used for cleanup
 *   (e.g., hiding a loading indicator).
 * @return `true` if the result was [ResultBiz.Success], `false` otherwise.
 */
suspend fun <R, T : R> dispatchBizResult(
    uiEventManager: UiEventManager?,
    bizResult: ResultBiz<T>,
    onSuccess: suspend (R, Any?) -> Unit,
    onBizError: (BusinessException, R?) -> Unit,
    onFailure: ((ResultException) -> Unit)? = null,
    onRelogin: ((BusinessException, R?) -> Unit)? = null,
    onLast: (() -> Unit)? = null,
): Boolean = when (bizResult) {
    is ResultBiz.Failure -> {
        d(TAG) { "dispatchBizResult -> Failure" }
        try {
            onFailure?.invoke(bizResult.exception()) ?: uiEventManager?.sendEvent(
                UiEvent.ShowToast(
                    message = bizResult.exception.message,
                    isError = true
                )
            )
        } finally {
            onLast?.invoke()
        }
        false
    }

    is ResultBiz.Relogin -> {
        d(TAG) { "dispatchBizResult -> Relogin" }
        onRelogin?.invoke(bizResult.exception, bizResult.getReloginErrData())
            ?: uiEventManager?.let {
                bizResult.exception.trySendReloginEvt(it)
            }
        false
    }

    is ResultBiz.Success -> {
        d(TAG) { "dispatchBizResult -> Success" }
        try {
            onSuccess(bizResult.get(), bizResult.extraData)
        } finally {
            onLast?.invoke()
        }
        true
    }

    is ResultBiz.BusinessError -> {
        d(TAG) { "dispatchBizResult -> BusinessError" }
        try {
            onBizError(bizResult.exception, bizResult.getBizErrData())
        } finally {
            onLast?.invoke()
        }
        false
    }
}

suspend fun ResultException.trySendReloginEvt(uiEvtMgr: UiEventManager): Boolean =
    if (this is ReloginException) {
        uiEvtMgr.sendEvent(UiEvent.Relogin)
        true
    } else {
        false
    }
