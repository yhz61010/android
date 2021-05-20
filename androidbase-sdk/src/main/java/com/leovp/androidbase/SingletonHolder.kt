package com.leovp.androidbase

/**
 * Author: Michael Leo
 * Date: 20-6-12 下午2:04
 *
 * Usage:
 * ```kotlin
 * class SomeSingleton private constructor(context: Context) {
 *     init {
 *         // Init using context argument
 *         context.getString(R.string.app_name)
 *     }
 *     companion object : SingletonHolder<SomeSingleton, Context>(::SomeSingleton)
 * }
 * ```
 * Get your singleton instance like this below:
 * ```kotlin
 * SomeSingleton.getInstance(context).doSomething()
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