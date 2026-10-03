package com.myremote.app.lg

import com.myremote.app.domain.InputSource
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject

internal data class LgMessage(val type: String, val id: String?, val payload: JSONObject?, val error: String?)

/** The small SSAP subset used by this remote. No Android UI or sockets live here. */
internal object LgProtocol {
    const val INPUT_LIST = "ssap://tv/getExternalInputList"
    const val SWITCH_INPUT = "ssap://tv/switchInput"
    const val TURN_OFF = "ssap://system/turnOff"

    private val permissions = listOf(
        "TEST_OPEN", "TEST_PROTECTED", "CONTROL_INPUT_TV", "READ_INPUT_DEVICE_LIST", "CONTROL_POWER",
    )

    fun hello(id: String): String = JSONObject()
        .put("type", "hello").put("id", id)
        .put("payload", JSONObject().put("appId", "com.myremote.app").put("appName", "My Remote"))
        .toString()

    fun register(id: String, clientKey: String?): String {
        val manifest = JSONObject().put("manifestVersion", 1).put("permissions", JSONArray(permissions))
        val payload = JSONObject().put("pairingType", "PROMPT").put("manifest", manifest)
        if (clientKey != null) payload.put("client-key", clientKey)
        return JSONObject().put("type", "register").put("id", id).put("payload", payload).toString()
    }

    fun request(id: String, uri: String, payload: JSONObject? = null): String {
        val message = JSONObject().put("type", "request").put("id", id).put("uri", uri)
        if (payload != null) message.put("payload", payload)
        return message.toString()
    }

    fun decode(text: String): LgMessage {
        val json = JSONObject(text)
        val type = json.optString("type")
        if (type.isBlank()) throw IOException("webOS message has no type")
        return LgMessage(type, json.optString("id").takeIf(String::isNotEmpty),
            json.optJSONObject("payload"), json.optString("error").takeIf(String::isNotEmpty))
    }

    fun clientKey(message: LgMessage): String? = message.payload?.optString("client-key")?.takeIf(String::isNotBlank)

    fun inputIds(message: LgMessage): Set<String> {
        val devices = message.payload?.optJSONArray("devices") ?: throw IOException("webOS did not return an input list")
        return buildSet {
            for (index in 0 until devices.length()) {
                devices.optJSONObject(index)?.optString("id")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    fun matchingInput(source: InputSource, available: Set<String>): String =
        available.firstOrNull { it.equals(source.webOsId, ignoreCase = true) }
            ?: throw IOException("${source.webOsId} was not reported by the TV")

    fun requireSuccess(message: LgMessage): LgMessage {
        if (message.type == "error") throw IOException(message.error ?: "webOS rejected the request")
        if (message.type != "response" || message.payload?.optBoolean("returnValue", true) == false) {
            throw IOException(message.payload?.optString("errorText")?.takeIf(String::isNotBlank)
                ?: "webOS request failed")
        }
        return message
    }
}

/** Correlates concurrent SSAP responses by ID; late replies cannot complete another request. */
internal class LgRequests {
    private val next = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<LgMessage>>()

    fun open(): Pair<String, CompletableDeferred<LgMessage>> {
        val id = "myremote_${next.getAndIncrement()}"
        val result = CompletableDeferred<LgMessage>()
        pending[id] = result
        return id to result
    }

    fun complete(message: LgMessage): Boolean {
        if (message.type != "response" && message.type != "error") return false
        val id = message.id ?: return false
        val result = pending.remove(id) ?: return false
        result.complete(message)
        return true
    }

    fun remove(id: String) { pending.remove(id)?.cancel() }

    fun failAll(error: Throwable) {
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
    }
}
