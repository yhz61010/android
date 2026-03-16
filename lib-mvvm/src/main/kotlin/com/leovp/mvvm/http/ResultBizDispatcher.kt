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

suspend fun <R, T : R> dispatchBizResult(
    uiEventManager: UiEventManager?,
    bizResult: ResultBiz<T>,
    onSuccess: suspend (R, Any?) -> Unit,
    onBizError: (BusinessException, R?) -> Unit,
    onFailure: ((ResultException) -> Unit)? = null,
    onRelogin: ((BusinessException, R?) -> Unit)? = null,
): Boolean = when (bizResult) {
    is ResultBiz.Failure -> {
        d(TAG) { "dispatchBizResult -> Failure" }
        onFailure?.invoke(bizResult.exception()) ?: uiEventManager?.sendEvent(
            UiEvent.ShowToast(
                message = bizResult.exception.message,
                isError = true
            )
        )
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
        onSuccess(bizResult.get(), bizResult.extraData)
        true
    }

    is ResultBiz.BusinessError -> {
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
