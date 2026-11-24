package com.leovp.opengl

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordinates a file producer with the GL consumer that owns the next draw. */
internal class AwaitedFrameRenderer(private val drawLock: Any = Any()) {
    private class Request(val prepare: () -> Unit) {
        val completed = CompletableDeferred<Unit>()
    }

    private val producerMutex = Mutex()
    private val pending = AtomicReference<Request?>(null)

    suspend fun render(prepare: () -> Unit, requestDraw: () -> Unit) = producerMutex.withLock {
        val request = Request(prepare)
        check(pending.compareAndSet(null, request)) { "A frame is already pending" }
        try {
            requestDraw()
            request.completed.await()
        } finally {
            pending.compareAndSet(request, null)
            request.completed.cancel()
        }
    }

    fun drawFrame(draw: () -> Unit) {
        val request = pending.getAndSet(null)
        try {
            synchronized(drawLock) {
                if (request?.completed?.isCancelled != true) request?.prepare?.invoke()
                draw()
            }
            // Complete outside the renderer lock: a resumed producer may immediately submit again.
            request?.completed?.complete(Unit)
        } catch (error: Exception) {
            request?.completed?.completeExceptionally(error)
            if (request == null) throw error
        } catch (error: Throwable) {
            request?.completed?.completeExceptionally(error)
            throw error
        }
    }
}
