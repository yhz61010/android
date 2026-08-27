package com.leovp.audio.base

import kotlinx.coroutines.CancellationException

/** Runs [block] without converting coroutine cancellation into a failed [Result]. */
internal inline fun <T> runCatchingPreservingCancellation(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
