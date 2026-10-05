package com.myremote.app.network

import com.myremote.app.domain.DeviceFailure
import com.myremote.app.domain.FailureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Deadline/cancellation closes THIS operation's native connection and joins the IO worker.
 * A coroutine timer alone cannot interrupt OutputStream.write/flush. close must unblock native IO.
 * Never look up a mutable controller's current socket here, and never replay uncertain bytes.
 */
internal object OwnedSocketWrite {
    suspend fun run(timeoutMillis: Long = 4_000, close: () -> Unit, write: () -> Unit) = supervisorScope {
        val worker = async(Dispatchers.IO) { write() }
        try {
            withTimeout(timeoutMillis) { worker.await() }
        } catch (error: Throwable) {
            runCatching(close)
            worker.cancel()
            withContext(NonCancellable) { worker.join() }
            currentCoroutineContext().ensureActive()
            if (error is TimeoutCancellationException)
                throw DeviceFailure(FailureKind.NETWORK, "Device write timed out", error)
            throw error
        }
    }
}
