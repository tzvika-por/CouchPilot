package com.myremote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myremote.app.ui.GoogleTvSetupDialog
import com.myremote.app.ui.RemoteScreen
import com.myremote.app.ui.RemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val remote: RemoteViewModel = viewModel()
            val state by remote.remoteState.collectAsStateWithLifecycle()
            val devices by remote.discoveredDevices.collectAsStateWithLifecycle()
            val connection by remote.streamerState.collectAsStateWithLifecycle()
            val error by remote.streamerError.collectAsStateWithLifecycle()
            val discoveryError by remote.discoveryError.collectAsStateWithLifecycle()
            val setupVisible by remote.setupVisible.collectAsStateWithLifecycle()
            RemoteTheme {
                RemoteScreen(state = state, onAction = remote::dispatch, onConfigureXiaomi = remote::openSetup)
                if (setupVisible) GoogleTvSetupDialog(
                    devices = devices,
                    connection = connection,
                    error = error ?: discoveryError,
                    onDismiss = remote::closeSetup,
                    onDevice = remote::beginPairing,
                    onManualHost = remote::beginManualPairing,
                    onCode = remote::submitCode,
                    onRetry = remote::retry,
                    onForget = remote::forgetPairing,
                )
            }
        }
    }
}
