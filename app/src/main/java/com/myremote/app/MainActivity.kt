package com.myremote.app

import android.os.Bundle
import android.Manifest
import android.os.Build
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.myremote.app.ui.SamsungSetupDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myremote.app.ui.GoogleTvSetupDialog
import com.myremote.app.ui.LgSetupDialog
import com.myremote.app.ui.RemoteScreen
import com.myremote.app.ui.RemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val remote: RemoteViewModel = viewModel()
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(remote, lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) remote.startConnections()
                    if (event == Lifecycle.Event.ON_STOP) remote.stopConnections()
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer); remote.stopConnections() }
            }
            val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                remote.refreshSoundbarDevices()
            }
            val hidPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                remote.refreshHidHosts()
            }
            val streamerConnection by remote.streamerConnection.collectAsStateWithLifecycle()
            val hidRegistered by remote.hidRegistered.collectAsStateWithLifecycle()
            val hidHosts by remote.hidHosts.collectAsStateWithLifecycle()
            val hidError by remote.hidError.collectAsStateWithLifecycle()
            val hidPairing by remote.hidPairing.collectAsStateWithLifecycle()
            val hasBluetoothPermission by remote.bluetoothPermission.collectAsStateWithLifecycle()
            val soundbarSetup by remote.soundbarSetupVisible.collectAsStateWithLifecycle()
            val soundbarDevices by remote.soundbarDevices.collectAsStateWithLifecycle()
            val soundbarConnection by remote.soundbarState.collectAsStateWithLifecycle()
            val soundbarError by remote.soundbarError.collectAsStateWithLifecycle()
            val state by remote.remoteState.collectAsStateWithLifecycle()
            val devices by remote.discoveredDevices.collectAsStateWithLifecycle()
            val connection by remote.streamerState.collectAsStateWithLifecycle()
            val error by remote.streamerError.collectAsStateWithLifecycle()
            val discoveryError by remote.discoveryError.collectAsStateWithLifecycle()
            val setupVisible by remote.setupVisible.collectAsStateWithLifecycle()
            val lgSetupVisible by remote.lgSetupVisible.collectAsStateWithLifecycle()
            val lgDevices by remote.discoveredLgDevices.collectAsStateWithLifecycle()
            val lgConnection by remote.tvState.collectAsStateWithLifecycle()
            val lgError by remote.tvError.collectAsStateWithLifecycle()
            val lgDiscoveryError by remote.lgDiscoveryError.collectAsStateWithLifecycle()
            RemoteTheme {
                RemoteScreen(state = state, onAction = remote::dispatch,
                    onConfigureXiaomi = remote::openSetup, onConfigureLg = remote::openLgSetup,
                    onConfigureSamsung = remote::openSoundbarSetup)
                if (soundbarSetup) SamsungSetupDialog(
                    devices = soundbarDevices, connection = soundbarConnection, error = soundbarError,
                    hasPermission = hasBluetoothPermission,
                    onPermission = {
                        if (Build.VERSION.SDK_INT >= 31) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        else remote.refreshSoundbarDevices()
                    },
                    onDevice = remote::selectSoundbar, onRetry = remote::refreshSoundbarDevices,
                    onSettings = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    onForget = remote::forgetSoundbar, onDismiss = remote::closeSoundbarSetup,
                )
                if (setupVisible) GoogleTvSetupDialog(
                    devices = devices,
                    connection = connection,
                    error = if (streamerConnection == com.myremote.app.hid.StreamerConnection.LAN) error ?: discoveryError else null,
                    onDismiss = remote::closeSetup,
                    onDevice = remote::beginPairing,
                    onManualHost = remote::beginManualPairing,
                    onCode = remote::submitCode,
                    onRetry = remote::retry,
                    onForget = remote::forgetPairing,
                    bluetoothMode = streamerConnection == com.myremote.app.hid.StreamerConnection.BLUETOOTH,
                    onLan = { remote.useLanConnection() }, onBluetooth = remote::useBluetoothConnection,
                    bluetoothContent = {
                        com.myremote.app.ui.HidSetupContent(
                            hosts = hidHosts, hasPermission = hasBluetoothPermission,
                            supported = remote.hidBluetooth.supported, registered = hidRegistered,
                            pairing = hidPairing, error = hidError,
                            onPermission = {
                                if (Build.VERSION.SDK_INT >= 31) hidPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                else remote.refreshHidHosts()
                            },
                            onPair = remote::pairHidHost, onHost = remote::selectHidHost,
                            onSettings = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        )
                    },
                )
                if (lgSetupVisible) LgSetupDialog(
                    devices = lgDevices,
                    connection = lgConnection,
                    error = lgError ?: lgDiscoveryError,
                    onDismiss = remote::closeLgSetup,
                    onDevice = remote::selectLg,
                    onManualHost = remote::manualLg,
                    onRetry = remote::retryLg,
                    onForget = remote::forgetLg,
                    onRefreshAuthorization = remote::refreshLgAuthorization,
                )
            }
        }
    }
}
