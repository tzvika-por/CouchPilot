package com.myremote.app.diagnostics

import android.util.Log
import com.myremote.app.BuildConfig

/** Allowlisted metadata only. Never accepts packet bodies, keys, pairing codes, MACs or hostnames. */
object RemoteDiagnostics {
    data class Event(val device: String, val operation: String, val outcome: String, val code: Int?)
    private val history = ArrayDeque<Event>()
    private val identifier = Regex("[A-Za-z_]{1,48}")
    private val devices = setOf("lg", "google", "samsung", "remote")
    @Synchronized fun record(device: String, operation: String, outcome: String, code: Int? = null) {
        require(device in devices)
        require(identifier.matches(operation))
        require(identifier.matches(outcome))
        val event = Event(device, operation, outcome, code)
        if (history.size == 100) history.removeFirst()
        history.addLast(event)
        if (BuildConfig.DEBUG) Log.d("CouchPilot", "device=$device operation=$operation outcome=$outcome code=${code ?: 0}")
    }
    @Synchronized fun snapshot(): List<Event> = history.toList()
}
