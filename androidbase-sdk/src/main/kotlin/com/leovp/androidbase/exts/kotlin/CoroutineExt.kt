package com.leovp.androidbase.exts.kotlin

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

/**
 * Author: Michael Leo
 * Date: 2020/9/3 下午4:23
 */
@DelicateCoroutinesApi
suspend inline fun <R> withCancellableContext(context: CoroutineContext, crossinline block: CoroutineScope.() -> R): R {
    val job = GlobalScope.async(context) {
        runCatching(block)
    }
    val result = try {
        job.await()
    } catch (e: CancellationException) {
        job.cancel()
        throw e
    }
    return result.getOrThrow()
}
