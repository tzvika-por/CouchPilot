package com.myremote.app.lg

import com.myremote.app.domain.InputSource
import com.myremote.app.domain.DeviceFailure
import com.myremote.app.domain.FailureKind
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject

internal data class LgInput(val id: String, val appId: String?)

internal data class LgMessage(
    val type: String, val id: String?, val payload: JSONObject?, val error: String?, val errorCode: Int? = null,
)

/** Keep the TV's authorization code and reason for diagnostics without exposing protocol data in UI. */
internal class LgAuthorizationException(val protocolError: String) : DeviceFailure(FailureKind.PERMISSION_DENIED, "LG denied this operation") {
    val errorCode: Int = 401
}

/** The small SSAP subset used by this remote. No Android UI or sockets live here. */
internal object LgProtocol {
    const val INPUT_LIST = "ssap://tv/getExternalInputList"
    const val SWITCH_INPUT = "ssap://tv/switchInput"
    const val LAUNCH_INPUT = "ssap://system.launcher/launch"
    const val TURN_OFF = "ssap://system/turnOff"

    // Records the requested permission contract; revision changes never force pairing again.
    const val AUTHORIZATION_REVISION = 3
    private val permissions = listOf(
        "READ_INPUT_DEVICE_LIST", "CONTROL_INPUT_TV", "CONTROL_DISPLAY", "CONTROL_POWER", "LAUNCH",
    )

    fun hello(id: String): String = JSONObject()
        .put("type", "hello").put("id", id)
        .put("payload", JSONObject().put("appId", "com.myremote.app").put("appName", "My Remote"))
        .toString()

    fun register(id: String, clientKey: String?): String {
        val manifest = JSONObject().put("manifestVersion", 1).put("appVersion", "1.0").put("permissions", JSONArray(permissions))
        val payload = JSONObject().put("pairingType", "PROMPT").put("forcePairing", false).put("manifest", manifest)
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
            json.optJSONObject("payload"), json.optString("error").takeIf(String::isNotEmpty),
            json.optInt("errorCode", 0).takeIf { it != 0 })
    }

    fun clientKey(message: LgMessage): String? = message.payload?.optString("client-key")?.takeIf(String::isNotBlank)

    fun deviceUuid(message: LgMessage): String? {
        if (message.type != "hello") return null
        val value = message.payload?.opt("deviceUUID") as? String ?: return null
        val normalized = normalizedLgUuid(value) ?: return null
        return normalized.takeIf { Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}").matches(it) }
    }

    fun inputs(message: LgMessage): List<LgInput> {
        val devices = message.payload?.optJSONArray("devices") ?: throw IOException("webOS did not return an input list")
        return buildList {
            for (index in 0 until devices.length()) {
                val device = devices.optJSONObject(index) ?: continue
                val id = device.optString("id").takeIf(String::isNotBlank) ?: continue
                add(LgInput(id, device.optString("appId").takeIf(String::isNotBlank)))
            }
        }
    }

    fun inputIds(message: LgMessage): Set<String> = inputs(message).map { it.id }.toSet()

    fun matchingInput(source: InputSource, available: Set<String>): String =
        available.firstOrNull { it.equals(source.webOsId, ignoreCase = true) }
            ?: throw IOException("${source.webOsId} was not reported by the TV")

    fun authorizationFailure(message: LgMessage): LgAuthorizationException? {
        if (message.type != "error" && message.payload?.optBoolean("returnValue", true) != false) return null
        val reason = message.error ?: message.payload?.optString("errorText").orEmpty()
        val code = message.errorCode ?: message.payload?.optInt("errorCode", 0)?.takeIf { it != 0 }
            ?: Regex("^\\s*(\\d{3})(?:\\s|$)").find(reason)?.groupValues?.get(1)?.toIntOrNull()
        return if (code == 401) LgAuthorizationException(reason) else null
    }

    fun requireSuccess(message: LgMessage): LgMessage {
        authorizationFailure(message)?.let { throw it }
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
