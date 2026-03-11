@file:Suppress("unused", "LongParameterList")

package com.leovp.network.http

import com.leovp.compose.composable.event.base.UiEvent
import com.leovp.compose.composable.event.base.UiEventManager
import com.leovp.log.base.d
import com.leovp.network.http.exception.ResultException
import com.leovp.network.http.exception.business.BusinessException
import com.leovp.network.http.exception.business.ReloginException

/**
 * Author: Michael Leo
 * Date: 2025/9/29 15:51
 */

private const val TAG = "ResultBiz"

suspend fun <R, T : R> dispatchBizResult(
    uiEventManager: UiEventManager?,
    bizResult: Result<T>,
    onSuccess: suspend (R, Any?) -> Unit,
    onBizError: (BusinessException, R?) -> Unit,
    onFailure: ((ResultException) -> Unit)? = null,
    onRelogin: ((BusinessException, R?) -> Unit)? = null,
): Boolean = when (bizResult) {
    is Result.Failure -> {
        d(TAG) { "dispatchBizResult -> Failure" }
        onFailure?.invoke(bizResult.exception()) ?: uiEventManager?.sendEvent(
            UiEvent.ShowToast(
                message = bizResult.exception.message,
                isError = true
            )
        )
        false
    }

    is Result.Relogin -> {
        d(TAG) { "dispatchBizResult -> Relogin" }
        onRelogin?.invoke(bizResult.exception, bizResult.getReloginErrData())
            ?: uiEventManager?.let {
                bizResult.exception.trySendReloginEvt(it)
            }
        false
    }

    is Result.Success -> {
        d(TAG) { "dispatchBizResult -> Success" }
        onSuccess(bizResult.get(), bizResult.extraData)
        true
    }

    is Result.BusinessError -> {
        d(TAG) { "dispatchBizResult -> BusinessError" }
        onBizError(bizResult.exception, bizResult.getBizErrData())
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
