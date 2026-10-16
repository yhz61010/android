package com.leovp.basenetty.framework.base

import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Awaits this Netty future without blocking the calling thread.
 *
 * Netty's own `sync()` / `syncUninterruptibly()` park the caller until the future completes. On a
 * coroutine dispatcher that costs a pool thread for the whole wait, and `syncUninterruptibly()`
 * additionally ignores interruption, so cancelling the caller does not shorten it by one
 * millisecond. Registering a listener leaves the thread free and makes the wait cancellable.
 *
 * Cancelling the caller only detaches the listener. Whatever is being awaited - a channel close or
 * an event loop shutdown - is already in flight and must be allowed to finish; abandoning it half
 * way would strand exactly the resources the caller is trying to release.
 *
 * A future that is already done is handled before suspending, and that is not an optimisation.
 * `addListener()` on a done future hands the notification to the future's executor, and when that
 * executor is a terminated event loop Netty only logs "Failed to submit a listener notification
 * task" and never calls the listener. A bind on a released server and a close on a channel whose
 * loop is already gone both return exactly such a future; awaited through a listener, either
 * would hang forever - `sync()` never had this problem because it checks `isDone()` first.
 *
 * A Netty future that was cancelled fails with `java.util.concurrent.CancellationException`, the
 * same class the coroutine machinery uses on the JVM. It is reported as [IllegalStateException]
 * instead so a caller cannot mistake the future's cancellation for its own.
 *
 * Internal on purpose: `basenetty` is published, and both callers live in this module. Making it
 * part of the public surface would commit the library to this signature for downstream users.
 */
internal suspend fun <V> Future<V>.awaitCompletion() {
    if (isDone) {
        failureOrNull()?.let { throw it }
        return
    }
    suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        val listener = GenericFutureListener<Future<V>> { future ->
            val failure = future.failureOrNull()
            if (failure == null) cont.resume(Unit) else cont.resumeWithException(failure)
        }
        addListener(listener)
        cont.invokeOnCancellation { removeListener(listener) }
    }
}

/**
 * The failure of this done future, or null when it succeeded.
 *
 * `cause()` is a platform type. A DefaultPromise that is done and not successful holds one, but a
 * custom Future implementation need not: passing null to `resumeWithException` would throw inside
 * the netty callback, where nothing resumes the continuation and the caller would wait out its
 * whole timeout - or forever, where there is none.
 */
private fun Future<*>.failureOrNull(): Throwable? = when {
    isSuccess -> null
    isCancelled -> IllegalStateException("Netty future was cancelled")
    else -> cause() ?: IllegalStateException("Netty future failed without a cause")
}
