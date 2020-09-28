package com.leovp.androidbase.http.retrofit.observers

import com.leovp.androidbase.http.retrofit.iter.ObserverOnNextListener
import com.leovp.androidbase.http.retrofit.observers.base.BaseProgressObserver

/**
 * Author: Michael Leo
 * Date: 19-7-24 下午5:08
 */
class NoProgressObserver<T>(listener: ObserverOnNextListener<T>) : BaseProgressObserver<T>(listener) {
    // If you do not want show any error information to UI in No Progress Dialog mode,
    // do not call super method and uncomment mListener.onError(e);
    @Suppress("unchecked")
    override fun onError(e: Throwable) {
        super.onError(e)
        //        mListener.onError(e);
    }
}