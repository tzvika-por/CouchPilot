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

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val tv = LgTvController(application)
    private val streamer = GoogleTvStreamerController(application)
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
    private val coordinator = RemoteCoordinator(tv, streamer, soundbar)
    private val _remoteState = MutableStateFlow(coordinator.state)
    val remoteState = _remoteState.asStateFlow()
    val streamerState = streamer.state
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
            streamer.state.collect { _remoteState.value = coordinator.updateStreamerConnection(it) }
        }
        viewModelScope.launch {
            tv.state.collect { _remoteState.value = coordinator.updateTvConnection(it) }
        }
        viewModelScope.launch {
            soundbar.state.collect { _remoteState.value = coordinator.updateSoundbarConnection(it) }
        }

    }

    fun startConnections() {
        if (bluetooth.hasPermission()) soundbar.retry()
        streamer.connectStored()
        tv.connectStored()
        if (_soundbarSetupVisible.value) refreshSoundbarDevices()
    }
    fun stopConnections() {
        pairingJob?.cancel()
        actionJobs.toList().forEach { it.cancel() }
        streamer.pause()
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
        runCatching { streamer.startDiscovery() }
    }

    fun closeSetup() {
        pairingJob?.cancel()
        pairingJob = null
        streamer.stopDiscovery()
        streamer.cancelPairing()
        _setupVisible.value = false
    }

    fun beginPairing(device: GoogleTvDevice) {
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

    fun retry() = streamer.retry()
    fun forgetPairing() { pairingJob?.cancel(); streamer.forgetPairing() }

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
    }
}
