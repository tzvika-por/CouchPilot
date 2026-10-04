package com.myremote.app.hid

import kotlinx.coroutines.flow.Flow

data class HidHost(val name: String, val address: String)
enum class HidEvent { CONNECTED, DISCONNECTED }
internal interface HidTransport : AutoCloseable {
    val bonded: Boolean
    val failure: com.myremote.app.domain.FailureKind? get() = null
    val events: Flow<HidEvent>
    fun reconnect()
    fun requestPairing()
    fun send(report: HidReport)
}
internal fun interface HidTransportFactory { suspend fun open(host: HidHost): HidTransport }
