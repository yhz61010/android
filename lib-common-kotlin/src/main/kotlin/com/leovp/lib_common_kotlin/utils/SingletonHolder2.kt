@file:Suppress("unused")

package com.leovp.lib_common_kotlin.utils

/**
 * Thread-Safe singleton holder.
 *
 * Usage:
 * ```kotlin
 * class SomeSingleton private constructor(context: Context, arg: String) {
 *     init {
 *         // Init using context argument
 *         context.getString(R.string.app_name)
 *     }
 *     companion object : SingletonHolder2<SomeSingleton, Context, String>(::SomeSingleton)
 * }
 * ```
 *  * Get your singleton instance like this below:
 * ```kotlin
 * SomeSingleton.getInstance(context, arg).doSomething()
 * ```
 *
 * Author: Michael Leo
 * Date: 20-12-24 下午2:56
 */
open class SingletonHolder2<out T, in A, in B>(creator: (A, B) -> T) {
    private var creator: ((A, B) -> T)? = creator

    @Volatile
    private var instance: T? = null

    fun getInstance(arg1: A, arg2: B): T = instance ?: synchronized(this) {
        val inst = instance ?: creator!!(arg1, arg2).also { instance = it }; creator = null; inst
    }
}
