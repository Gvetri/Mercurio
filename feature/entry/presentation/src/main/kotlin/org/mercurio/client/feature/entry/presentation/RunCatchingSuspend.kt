package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for a suspending block that never converts cancellation into a failure.
 *
 * A best-effort store write must not consume [CancellationException]: the surrounding job would
 * keep running after its holder closed. Every failure except cancellation becomes a failure
 * result, so the caller still decides what to do with it. The catch is deliberately generic for
 * the same reason [runCatching] has one: a non-cancellation failure must not escape a best-effort
 * store write.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> runCatchingSuspend(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
