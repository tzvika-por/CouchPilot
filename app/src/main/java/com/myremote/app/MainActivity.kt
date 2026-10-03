package com.myremote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.myremote.app.data.FakeSoundbarController
import com.myremote.app.data.FakeStreamerController
import com.myremote.app.data.FakeTvController
import com.myremote.app.domain.RemoteCoordinator
import com.myremote.app.ui.RemoteScreen
import com.myremote.app.ui.RemoteTheme

class MainActivity : ComponentActivity() {
    private val coordinator = RemoteCoordinator(
        tv = FakeTvController(),
        streamer = FakeStreamerController(),
        soundbar = FakeSoundbarController(),
    )
    private var remoteState by mutableStateOf(coordinator.state)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RemoteTheme {
                RemoteScreen(state = remoteState, onAction = { remoteState = coordinator.dispatch(it) })
            }
        }
    }
}
