package com.myremote.app.hid

/** OS bonding is separate from HID connection. Request booleans never establish readiness. */
internal enum class HidBondState { NONE, BONDING, BONDED }
internal class HidAssociation(
    private val state: () -> HidBondState,
    private val createBond: () -> Boolean,
    private val connect: () -> Unit,
    private val rejected: () -> Unit,
) {
    private var requested = false
    private var connecting = false
    private fun connectOnce() {
        if (!connecting) { connecting = true; connect() }
    }
    @Synchronized fun disconnected() { connecting = false }
    @Synchronized fun request() {
        when (state()) {
            HidBondState.BONDED -> connectOnce()
            HidBondState.BONDING -> Unit
            HidBondState.NONE -> if (!requested) {
                requested = createBond()
                if (!requested) rejected()
            }
        }
    }
    @Synchronized fun changed(previous: HidBondState, current: HidBondState) {
        when (current) {
            HidBondState.BONDED -> { requested = false; connectOnce() }
            HidBondState.NONE -> {
                requested = false
                connecting = false
                if (previous == HidBondState.BONDING) rejected()
            }
            HidBondState.BONDING -> Unit
        }
    }
}
