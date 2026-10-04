package com.myremote.app.hid

/** Host GET/SET_REPORT handling, including the keyboard LED output declared in SDP. */
internal class HidReports {
    private val input = mutableMapOf<Int, ByteArray>()
    private var leds = byteArrayOf(0)
    @Synchronized fun record(report: HidReport) { input[report.id] = report.bytes.copyOf() }
    @Synchronized fun clear() { input.clear(); leds = byteArrayOf(0) }
    @Synchronized fun get(type: Int, id: Int, limit: Int): ByteArray? {
        val bytes = when (type) {
            1 -> input[id] ?: HidProtocol.released(id)?.bytes
            2 -> if (id == 1) leds else null
            else -> null
        } ?: return null
        return if (limit < 0 || (limit != 0 && limit < bytes.size)) null else bytes.copyOf()
    }
    @Synchronized fun set(type: Int, id: Int, data: ByteArray): Boolean {
        if (type != 2 || id != 1 || data.size != 1) return false
        leds = data.copyOf()
        return true
    }
}
