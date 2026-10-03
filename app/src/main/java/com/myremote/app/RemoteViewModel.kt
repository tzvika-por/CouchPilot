package com.myremote.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myremote.app.data.FakeSoundbarController
import com.myremote.app.data.FakeTvController
import com.myremote.app.domain.RemoteAction
import com.myremote.app.domain.RemoteCoordinator
import com.myremote.app.google.GoogleTvDevice
import com.myremote.app.google.normalizedGoogleTvHost
import com.myremote.app.google.GoogleTvStreamerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val streamer = GoogleTvStreamerController(application)
    private val coordinator = RemoteCoordinator(FakeTvController(), streamer, FakeSoundbarController())
    private val _remoteState = MutableStateFlow(coordinator.state)
    val remoteState = _remoteState.asStateFlow()
    val streamerState = streamer.state
    val streamerError = streamer.error
    val discoveredDevices = streamer.discovery.devices
    val discoveryError = streamer.discovery.error
    private val _setupVisible = MutableStateFlow(false)
    val setupVisible = _setupVisible.asStateFlow()
    private var pairingJob: Job? = null

    init {
        viewModelScope.launch {
            streamer.state.collect { _remoteState.value = coordinator.updateStreamerConnection(it) }
        }
        streamer.connectStored()
    }

    fun dispatch(action: RemoteAction) {
        viewModelScope.launch { _remoteState.value = coordinator.dispatch(action) }
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
    fun forgetPairing() = streamer.forgetPairing()

    override fun onCleared() {
        streamer.close()
    }
}
