package com.leovp.lib_common_kotlin.utils

/**
 * Thread-Safe singleton holder.
 *
 * Usage:
 * ```kotlin
 * class SomeSingleton private constructor(app: Application) {
 *     init {
 *         // Init using context argument
 *         app.getString(R.string.app_name)
 *     }
 *     companion object : SingletonHolder<SomeSingleton, Application>(::SomeSingleton)
 * }
 * ```
 * Get your singleton instance like this below:
 * ```kotlin
 * SomeSingleton.getInstance(application).doSomething()
 * ```
 * Author: Michael Leo
 * Date: 20-6-12 下午2:04
 */
open class SingletonHolder<out T, in A>(creator: (A) -> T) {
    private var creator: ((A) -> T)? = creator

    @Volatile
    private var instance: T? = null

    fun getInstance(arg: A): T = instance ?: synchronized(this) {
        val inst = instance ?: creator!!(arg).also { instance = it }; creator = null; inst
    }
}