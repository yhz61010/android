@file:Suppress("unused")

package com.leovp.pref

import com.leovp.pref.base.AbsPref

/**
 * ```kotlin
 * // You'd better initialize IPref in `Application`
 * PrefContext.setPrefImp(LPref(context))
 * // Or define your custom Preference which extends `AbsPref`
 * PrefContext.setPrefImp(MMKVPref(context))
 * ```
 *
 * Author: Michael Leo
 * Date: 20-12-10 上午10:01
 */
object PrefContext {
    lateinit var pref: AbsPref
        private set

    fun setPrefImpl(pref: AbsPref) {
        this.pref = pref
    }

    fun isPrefInitialized(): Boolean = ::pref.isInitialized
}
