package com.myremote.app

import android.app.Application
import com.myremote.app.samsung.SamsungSoundbarController
import com.myremote.app.samsung.SamsungBluetooth
import com.myremote.app.samsung.SamsungDevice
import com.myremote.app.domain.RemoteAction
import com.myremote.app.domain.RemoteCoordinator
import com.myremote.app.google.GoogleTvDevice
import com.myremote.app.google.normalizedGoogleTvHost
import com.myremote.app.google.GoogleTvStreamerController
import com.myremote.app.lg.LgDevice
import com.myremote.app.lg.LgTvController
import com.myremote.app.lg.normalizedLgHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.stateIn

/** Process-owned state; device connections are leased by RemoteConnectionService, not an Activity. */
class RemoteSession(application: Application) : AutoCloseable {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private val tv = LgTvController(application)
    private val streamer = GoogleTvStreamerController(application)
    private val hidStore = com.myremote.app.hid.HidStore(application)
    val hidBluetooth = com.myremote.app.hid.AndroidHidBluetooth(application)
    private val hid = com.myremote.app.hid.HidStreamerController(hidBluetooth.factory,
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO),
        hidStore::host, hidStore::host)
    private val selectedConnection = MutableStateFlow(hidStore.mode())
    val streamerConnection = selectedConnection.asStateFlow()
    val hidRegistered = hid.registered
    val hidPairing = hid.pairing
    val hidBonded = hid.bonded
    fun pairHidHost() { if (connectionsRequired()) hid.requestPairing() }
    val hidError = hid.error
    private val mutableHidHosts = MutableStateFlow<List<com.myremote.app.hid.HidHost>>(emptyList())
    val hidHosts = mutableHidHosts.asStateFlow()
    private val route = com.myremote.app.hid.StreamerRoute(streamer, hid) { selectedConnection.value }
    private val soundbar = SamsungSoundbarController(application)
    val bluetooth = SamsungBluetooth(application)
    private val _bluetoothPermission = MutableStateFlow(bluetooth.hasPermission())
    val bluetoothPermission = _bluetoothPermission.asStateFlow()
    val soundbarState = soundbar.state
    val soundbarError = soundbar.error
    private val _soundbarDevices = MutableStateFlow<List<SamsungDevice>>(emptyList())
    val soundbarDevices = _soundbarDevices.asStateFlow()
    private val _soundbarSetupVisible = MutableStateFlow(false)
    val soundbarSetupVisible = _soundbarSetupVisible.asStateFlow()
    private val uiPreferences = application.getSharedPreferences("remote_ui_state", android.content.Context.MODE_PRIVATE)
    private val initialInput = uiPreferences.getString("last_input", null)?.let { name ->
        com.myremote.app.domain.InputSource.entries.firstOrNull { it.name == name }
    }
    private val coordinator = RemoteCoordinator(tv, route, soundbar, initialInput,
        observedStreamerPower = {
            if (selectedConnection.value == com.myremote.app.hid.StreamerConnection.LAN) streamer.powerState.value else null
        })
    private val _remoteState = MutableStateFlow(coordinator.state)
    val remoteState = _remoteState.asStateFlow()
    private val commands: com.myremote.app.domain.CommandScheduler = com.myremote.app.domain.CommandScheduler(scope, coordinator::target,
        { action, target ->
            _remoteState.value = coordinator.dispatch(action, target)
            if (action is RemoteAction.SelectInput && coordinator.state.selectedInput == action.source &&
                coordinator.state.failureDevice != com.myremote.app.domain.CommandDevice.TV) {
                commands.sourceChanged(action.source)
                uiPreferences.edit().putString("last_input", action.source.name).apply()
            }
        })
    val streamerState = kotlinx.coroutines.flow.combine(selectedConnection, streamer.state, hid.state) { mode, lan, bluetooth ->
        if (mode == com.myremote.app.hid.StreamerConnection.LAN) lan else bluetooth
    }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, route.connectionState)
    val streamerError = streamer.error
    val discoveredDevices = streamer.discovery.devices
    val discoveryError = streamer.discovery.error
    val tvState = tv.state
    val tvError = tv.error
    val discoveredLgDevices = tv.discovery.devices
    val lgDiscoveryError = tv.discovery.error
    private val _lgSetupVisible = MutableStateFlow(false)
    val lgSetupVisible = _lgSetupVisible.asStateFlow()
    private val _setupVisible = MutableStateFlow(false)
    val setupVisible = _setupVisible.asStateFlow()
    private var pairingJob: Job? = null

    init {
        scope.launch { commands.busy.collect { _remoteState.value = coordinator.updateBusy(it) } }
        scope.launch {
            streamer.powerState.collect { observation ->
                if (observation != null && selectedConnection.value == com.myremote.app.hid.StreamerConnection.LAN)
                    _remoteState.value = coordinator.updateStreamerPower(observation)
            }
        }
        scope.launch {
            streamerState.collect { _remoteState.value = coordinator.updateStreamerConnection(it) }
        }
        scope.launch {
            tv.state.collect { _remoteState.value = coordinator.updateTvConnection(it) }
        }
        scope.launch {
            soundbar.muted.collect { _remoteState.value = coordinator.updateSoundbarMute(it) }
        }
        scope.launch {
            soundbar.state.collect { _remoteState.value = coordinator.updateSoundbarConnection(it) }
        }

    }

    private val connectionLifetime = com.myremote.app.domain.ConnectionLifetime(
        open = ::openConnections, close = ::pauseConnections,
    )
    val connectionSessionActive = connectionLifetime.active
    val mayAutoStartConnections: Boolean get() = connectionLifetime.mayAutoStart

    fun startConnections() = connectionLifetime.start()
    fun stopConnections() = connectionLifetime.stop()

    /** Only discovery belongs to screen visibility. Established connections and key releases remain alive. */
    fun onUiHidden() {
        streamer.stopDiscovery()
        tv.stopDiscovery()
    }
    fun onUiVisible() {
        if (_setupVisible.value && selectedConnection.value == com.myremote.app.hid.StreamerConnection.LAN)
            runCatching { streamer.startDiscovery() }
        if (_lgSetupVisible.value) runCatching { tv.startDiscovery() }
    }
    fun connectionServiceFailed(error: Exception) {
        _remoteState.value = coordinator.reportFailure(error)
    }

    private fun openConnections() {
        if (bluetooth.hasPermission()) soundbar.connectStored()
        if (selectedConnection.value == com.myremote.app.hid.StreamerConnection.LAN) streamer.connectStored() else hid.retry()
        tv.connectStored()
        if (_soundbarSetupVisible.value) refreshSoundbarDevices()
    }
    private fun pauseConnections() {
        pairingJob?.cancel()
        commands.cancelAll()
        streamer.pause()
        hid.pause()
        tv.pause()
        soundbar.disconnect()
    }

    fun dispatch(action: RemoteAction) {
        if (!connectionsRequired()) return
        if (!commands.submit(action)) _remoteState.value = coordinator.reportFailure(
            com.myremote.app.domain.DeviceFailure(com.myremote.app.domain.FailureKind.UNAVAILABLE, "Command queue busy"))
    }

    private fun connectionsRequired(): Boolean {
        if (connectionLifetime.whileActive {}) return true
        _remoteState.value = coordinator.reportFailure(com.myremote.app.domain.DeviceFailure(
            com.myremote.app.domain.FailureKind.NOT_CONNECTED, "Resume connections before using remote controls"))
        return false
    }

    fun openSetup() {
        _setupVisible.value = true
        if (selectedConnection.value == com.myremote.app.hid.StreamerConnection.LAN) runCatching { streamer.startDiscovery() }
        else refreshHidHosts()
    }

    fun closeSetup() {
        pairingJob?.cancel()
        pairingJob = null
        streamer.stopDiscovery()
        streamer.cancelPairing()
        _setupVisible.value = false
    }

    fun beginPairing(device: GoogleTvDevice) {
        if (!connectionsRequired()) return
        commands.cancelDevice(com.myremote.app.domain.CommandDevice.STREAMER)
        useLanConnection(connect = false)
        pairingJob?.cancel()
        pairingJob = scope.launch { runCatching { streamer.beginPairing(device) } }
    }

    fun beginManualPairing(host: String) {
        val cleaned = normalizedGoogleTvHost(host) ?: return
        beginPairing(GoogleTvDevice(cleaned, cleaned))
    }

    fun submitCode(code: String) {
        if (!connectionsRequired()) return
        pairingJob = scope.launch { runCatching { streamer.finishPairing(code) } }
    }

    fun retry() {
        if (!connectionsRequired()) return
        if (selectedConnection.value == com.myremote.app.hid.StreamerConnection.LAN) streamer.retry() else hid.retry()
    }
    fun forgetPairing() {
        commands.cancelDevice(com.myremote.app.domain.CommandDevice.STREAMER)
        if (selectedConnection.value == com.myremote.app.hid.StreamerConnection.LAN) {
            pairingJob?.cancel(); streamer.forgetPairing()
        } else hid.forget()
    }
    fun useLanConnection(connect: Boolean = true) {
        commands.cancelDevice(com.myremote.app.domain.CommandDevice.STREAMER)
        hid.pause()
        hidStore.mode(com.myremote.app.hid.StreamerConnection.LAN)
        selectedConnection.value = com.myremote.app.hid.StreamerConnection.LAN
        if (connect && connectionSessionActive.value) { streamer.connectStored(); if (_setupVisible.value) streamer.startDiscovery() }
    }
    fun useBluetoothConnection() {
        commands.cancelDevice(com.myremote.app.domain.CommandDevice.STREAMER)
        pairingJob?.cancel(); streamer.pause()
        hidStore.mode(com.myremote.app.hid.StreamerConnection.BLUETOOTH)
        selectedConnection.value = com.myremote.app.hid.StreamerConnection.BLUETOOTH
        refreshHidHosts()
        if (connectionSessionActive.value && hidStore.host() != null) hid.retry()
    }
    fun selectHidHost(host: com.myremote.app.hid.HidHost) { if (!connectionsRequired()) return; commands.cancelDevice(com.myremote.app.domain.CommandDevice.STREAMER); hid.select(host) }
    fun refreshHidHosts() {
        _bluetoothPermission.value = bluetooth.hasPermission()
        mutableHidHosts.value = runCatching { hidBluetooth.pairedHosts() }.getOrDefault(emptyList())
        if (connectionSessionActive.value && selectedConnection.value == com.myremote.app.hid.StreamerConnection.BLUETOOTH) hid.retry()
    }

    fun openLgSetup() {
        _lgSetupVisible.value = true
        runCatching { tv.startDiscovery() }
    }

    fun closeLgSetup() {
        tv.stopDiscovery()
        _lgSetupVisible.value = false
    }

    fun selectLg(device: LgDevice) { if (!connectionsRequired()) return; commands.cancelDevice(com.myremote.app.domain.CommandDevice.TV); tv.select(device) }

    fun manualLg(host: String) {
        val cleaned = normalizedLgHost(host) ?: return
        selectLg(LgDevice("LG TV", cleaned))
    }

    fun configureLgWake(address: String) = tv.configureWakeAddress(address)

    fun retryLg() { if (connectionsRequired()) tv.retry() }
    fun forgetLg() { commands.cancelDevice(com.myremote.app.domain.CommandDevice.TV); tv.forgetPairing() }
    fun refreshLgAuthorization() { if (!connectionsRequired()) return; commands.cancelDevice(com.myremote.app.domain.CommandDevice.TV); tv.refreshAuthorization() }

    fun openSoundbarSetup() {
        _soundbarSetupVisible.value = true
        refreshSoundbarDevices()
    }
    fun refreshSoundbarDevices() {
        _bluetoothPermission.value = bluetooth.hasPermission()
        _soundbarDevices.value = runCatching { bluetooth.pairedDevices() }.getOrDefault(emptyList())
        if (connectionSessionActive.value && bluetooth.hasPermission()) soundbar.connectStored()
    }
    fun closeSoundbarSetup() { _soundbarSetupVisible.value = false }
    fun selectSoundbar(device: SamsungDevice) { if (!connectionsRequired()) return; commands.cancelDevice(com.myremote.app.domain.CommandDevice.SOUNDBAR); soundbar.select(device) }
    fun retrySoundbar() { if (connectionsRequired()) soundbar.retry() }
    fun forgetSoundbar() { commands.cancelDevice(com.myremote.app.domain.CommandDevice.SOUNDBAR); soundbar.forget() }

    override fun close() {
        stopConnections()
        commands.close()
        soundbar.close()
        tv.close()
        streamer.close()
        hid.close()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
