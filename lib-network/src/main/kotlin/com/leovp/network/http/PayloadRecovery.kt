package com.leovp.network.http

import com.leovp.log.base.e
import kotlinx.coroutines.CancellationException

/** Downgrades recoverable payload failures while preserving cancellation and fatal errors. */
@PublishedApi
internal inline fun <R> payloadOperationOrNull(errorMessage: String, operation: () -> R): R? = try {
    operation()
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    e("ResultExt", error) { errorMessage }
    null
}
