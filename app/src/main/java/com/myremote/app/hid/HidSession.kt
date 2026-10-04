package com.myremote.app.hid

import com.myremote.app.domain.PressKind
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Entire press owns the writer; cancellation releases the key before cleanup. */
internal class HidSession(private val send: (HidReport) -> Unit) {
    private val writer = Mutex()
    suspend fun press(report: HidReport, kind: PressKind) = writer.withLock {
        send(report)
        try { delay(if (kind == PressKind.LONG) 650 else 60) }
        finally { withContext(NonCancellable) { send(requireNotNull(HidProtocol.released(report.id))) } }
    }
}
