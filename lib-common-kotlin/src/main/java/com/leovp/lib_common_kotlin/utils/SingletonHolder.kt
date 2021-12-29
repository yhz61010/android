package com.leovp.lib_common_kotlin.utils

/**
 * Author: Michael Leo
 * Date: 20-6-12 下午2:04
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
 */
open class SingletonHolder<out T, in A>(creator: (A) -> T) {
    private var creator: ((A) -> T)? = creator

    @Volatile
    private var instance: T? = null

    fun getInstance(arg: A): T {
        val ins = instance
        if (ins != null) {
            return ins
        }

        return synchronized(this) {
            val inst = instance
            if (inst != null) {
                inst
            } else {
                val created = creator!!(arg)
                instance = created
                creator = null
                created
            }
        }
    }
}