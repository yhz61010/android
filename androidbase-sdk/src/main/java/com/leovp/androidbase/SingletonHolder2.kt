package com.leovp.androidbase

/**
 * Author: Michael Leo
 * Date: 20-12-24 下午2:56
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
 */
open class SingletonHolder2<out T, in A, in B>(creator: (A, B) -> T) {
    private var creator: ((A, B) -> T)? = creator

    @Volatile
    private var instance: T? = null

    fun getInstance(arg1: A, arg2: B): T {
        val ins = instance
        if (ins != null) {
            return ins
        }

        return synchronized(this) {
            val inst = instance
            if (inst != null) {
                inst
            } else {
                val created = creator!!(arg1, arg2)
                instance = created
                creator = null
                created
            }
        }
    }
}