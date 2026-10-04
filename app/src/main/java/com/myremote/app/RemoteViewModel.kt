package com.myremote.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
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
    fun pairHidHost() = hid.requestPairing()
    val hidError = hid.error
    private val mutableHidHosts = MutableStateFlow<List<com.myremote.app.hid.HidHost>>(emptyList())
    val hidHosts = mutableHidHosts.asStateFlow()
    private val route = com.myremote.app.hid.StreamerRoute(streamer, hid) { selectedConnection.value }
    private val soundbar = SamsungSoundbarController(application)
    val bluetooth = SamsungBluetooth(application)
    private val _bluetoothPermission = MutableStateFlow(bluetooth.hasPermission())
    val bluetoothPermission = _bluetoothPermission.asStateFlow()
    private val actionJobs = mutableSetOf<Job>()
    val soundbarState = soundbar.state
    val soundbarError = soundbar.error
    private val _soundbarDevices = MutableStateFlow<List<SamsungDevice>>(emptyList())
    val soundbarDevices = _soundbarDevices.asStateFlow()
    private val _soundbarSetupVisible = MutableStateFlow(false)
    val soundbarSetupVisible = _soundbarSetupVisible.asStateFlow()
    private val coordinator = RemoteCoordinator(tv, route, soundbar)
    private val _remoteState = MutableStateFlow(coordinator.state)
    val remoteState = _remoteState.asStateFlow()
    val streamerState = kotlinx.coroutines.flow.combine(selectedConnection, streamer.state, hid.state) { mode, lan, bluetooth ->
        if (mode == com.myremote.app.hid.StreamerConnection.LAN) lan else bluetooth
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, route.connectionState)
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
        viewModelScope.launch {
            streamer.powerState.collect { on ->
                if (on != null && selectedConnection.value == com.myremote.app.hid.StreamerConnection.LAN) _remoteState.value = coordinator.updateStreamerPower(on)
            }
        }
        viewModelScope.launch {
            streamerState.collect { _remoteState.value = coordinator.updateStreamerConnection(it) }
        }
        viewModelScope.launch {
            tv.state.collect { _remoteState.value = coordinator.updateTvConnection(it) }
        }
        viewModelScope.launch {
            soundbar.muted.collect { _remoteState.value = coordinator.updateSoundbarMute(it) }
        }
        viewModelScope.launch {
            soundbar.state.collect { _remoteState.value = coordinator.updateSoundbarConnection(it) }
        }

    }

    fun startConnections() {
        if (bluetooth.hasPermission()) soundbar.retry()
        if (selectedConnection.value == com.myremote.app.hid.StreamerConnection.LAN) streamer.connectStored() else hid.retry()
        tv.connectStored()
        if (_soundbarSetupVisible.value) refreshSoundbarDevices()
    }
    fun stopConnections() {
        pairingJob?.cancel()
        actionJobs.toList().forEach { it.cancel() }
        streamer.pause()
        hid.pause()
        tv.pause()
        soundbar.disconnect()
    }

    fun dispatch(action: RemoteAction) {
        val job = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            _remoteState.value = coordinator.dispatch(action)
        }
        actionJobs += job
        job.invokeOnCompletion { actionJobs.remove(job) }
        job.start()
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
        useLanConnection(connect = false)
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch { runCatching { streamer.beginPairing(device) } }
    }

    fun beginManualPairing(host: String) {
        val cleaned = normalizedGoogleTvHost(host) ?: return
        beginPairing(GoogleTvDevice(cleaned, cleaned))
    }

    fun submitCode(code: String) {
        pairingJob = viewModelScope.launch { runCatching { streamer.finishPairing(code) } }
    }

    fun retry() {
        if (selectedConnection.value == com.myremote.app.hid.StreamerConnection.LAN) streamer.retry() else hid.retry()
    }
    fun forgetPairing() {
        if (selectedConnection.value == com.myremote.app.hid.StreamerConnection.LAN) {
            pairingJob?.cancel(); streamer.forgetPairing()
        } else hid.forget()
    }
    fun useLanConnection(connect: Boolean = true) {
        hid.pause()
        hidStore.mode(com.myremote.app.hid.StreamerConnection.LAN)
        selectedConnection.value = com.myremote.app.hid.StreamerConnection.LAN
        if (connect) { streamer.connectStored(); if (_setupVisible.value) streamer.startDiscovery() }
    }
    fun useBluetoothConnection() {
        pairingJob?.cancel(); streamer.pause()
        hidStore.mode(com.myremote.app.hid.StreamerConnection.BLUETOOTH)
        selectedConnection.value = com.myremote.app.hid.StreamerConnection.BLUETOOTH
        refreshHidHosts()
        if (hidStore.host() == null) hid.select(com.myremote.app.hid.XiaomiInstallation.observedHost) else hid.retry()
    }
    fun selectHidHost(host: com.myremote.app.hid.HidHost) { hid.select(host) }
    fun refreshHidHosts() {
        _bluetoothPermission.value = bluetooth.hasPermission()
        mutableHidHosts.value = runCatching { hidBluetooth.pairedHosts() }.getOrDefault(emptyList())
        if (selectedConnection.value == com.myremote.app.hid.StreamerConnection.BLUETOOTH) hid.retry()
    }

    fun openLgSetup() {
        _lgSetupVisible.value = true
        runCatching { tv.startDiscovery() }
    }

    fun closeLgSetup() {
        tv.stopDiscovery()
        _lgSetupVisible.value = false
    }

    fun selectLg(device: LgDevice) = tv.select(device)

    fun manualLg(host: String) {
        val cleaned = normalizedLgHost(host) ?: return
        tv.select(LgDevice("LG TV", cleaned))
    }

    fun retryLg() = tv.retry()
    fun forgetLg() = tv.forgetPairing()
    fun refreshLgAuthorization() = tv.refreshAuthorization()

    fun openSoundbarSetup() {
        _soundbarSetupVisible.value = true
        refreshSoundbarDevices()
    }
    fun refreshSoundbarDevices() {
        _bluetoothPermission.value = bluetooth.hasPermission()
        _soundbarDevices.value = runCatching { bluetooth.pairedDevices() }.getOrDefault(emptyList())
        if (bluetooth.hasPermission()) soundbar.retry()
    }
    fun closeSoundbarSetup() { _soundbarSetupVisible.value = false }
    fun selectSoundbar(device: SamsungDevice) = soundbar.select(device)
    fun retrySoundbar() = soundbar.retry()
    fun forgetSoundbar() = soundbar.forget()

    override fun onCleared() {
        soundbar.close()
        tv.close()
        streamer.close()
        hid.close()
    }
}
