@file:Suppress("unused")

package com.leovp.androidbase.utils.pref

import com.leovp.androidbase.utils.pref.base.IPref

/**
 * ```kotlin
 * // You'd better initialize IPref in `Application`
 * PrefContext.setPrefImp(LPref())
 * // Or define your custom Preference which implements `IPref`
 * PrefContext.setPrefImp(MMKVPref())
 * ```
 *
 * Author: Michael Leo
 * Date: 20-12-10 上午10:01
 */
object PrefContext {
    lateinit var pref: IPref
        private set

    fun setPrefImp(pref: IPref) {
        this.pref = pref
    }

    fun isPrefInitialized(): Boolean = ::pref.isInitialized
}