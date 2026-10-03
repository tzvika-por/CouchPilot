package com.myremote.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.myremote.app.R
import com.myremote.app.domain.ActiveDevice
import com.myremote.app.domain.ConnectionState
import com.myremote.app.domain.InputSource
import com.myremote.app.domain.RemoteAction
import com.myremote.app.domain.RemoteKey
import com.myremote.app.domain.RemoteState

@Composable
fun RemoteScreen(state: RemoteState, onAction: (RemoteAction) -> Unit,
    onConfigureXiaomi: () -> Unit = {}, onConfigureLg: () -> Unit = {}) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.remote_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.demo_mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            StatusPanel(state, onConfigureXiaomi, onConfigureLg)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoteButton(
                    label = stringResource(R.string.power),
                    tag = "power",
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(RemoteAction.Power) },
                )
                RemoteButton(
                    label = stringResource(R.string.watch_yes),
                    tag = "watch_yes",
                    modifier = Modifier.weight(1.5f),
                    emphasized = true,
                    onClick = { onAction(RemoteAction.WatchYesPlus) },
                )
            }

            SectionTitle(R.string.sources)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceButton(InputSource.PS5, R.string.ps5, state, onAction, Modifier.weight(1f))
                SourceButton(InputSource.MAC_MINI, R.string.mac_mini, state, onAction, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceButton(InputSource.XIAOMI, R.string.streamer, state, onAction, Modifier.weight(1f))
                SourceButton(InputSource.PC, R.string.pc, state, onAction, Modifier.weight(1f))
            }

            SectionTitle(R.string.sound)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoteButton(stringResource(R.string.volume_down), "volume_down", Modifier.weight(1f)) {
                    onAction(RemoteAction.VolumeDown)
                }
                RemoteButton(stringResource(R.string.mute), "mute", Modifier.weight(1f)) {
                    onAction(RemoteAction.Mute)
                }
                RemoteButton(stringResource(R.string.volume_up), "volume_up", Modifier.weight(1f)) {
                    onAction(RemoteAction.VolumeUp)
                }
            }

            SectionTitle(R.string.channels)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoteButton(stringResource(R.string.channel_down), "channel_down", Modifier.weight(1f)) {
                    onAction(RemoteAction.ChannelDown)
                }
                RemoteButton(stringResource(R.string.last_channel), "last_channel", Modifier.weight(1.25f)) {
                    onAction(RemoteAction.LastChannel)
                }
                RemoteButton(stringResource(R.string.channel_up), "channel_up", Modifier.weight(1f)) {
                    onAction(RemoteAction.ChannelUp)
                }
            }

            NumberPad(onAction)
            SectionTitle(R.string.navigation)
            DirectionPad(onAction)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoteButton(stringResource(R.string.back), "back", Modifier.weight(1f)) {
                    onAction(RemoteAction.Key(RemoteKey.BACK))
                }
                RemoteButton(stringResource(R.string.home), "home", Modifier.weight(1f)) {
                    onAction(RemoteAction.Key(RemoteKey.HOME))
                }
                RemoteButton(stringResource(R.string.play_pause), "play_pause", Modifier.weight(1.2f)) {
                    onAction(RemoteAction.Key(RemoteKey.PLAY_PAUSE))
                }
            }
            state.errorMessage?.let {
                Text(stringResource(R.string.action_failed, it), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatusPanel(state: RemoteState, onConfigureXiaomi: () -> Unit, onConfigureLg: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            val active = if (state.activeDevice == ActiveDevice.TV) R.string.tv else R.string.streamer
            Text(
                stringResource(R.string.active_device, stringResource(active)),
                modifier = Modifier.testTag("active_device"),
                style = MaterialTheme.typography.titleMedium,
            )
            StatusLine(R.string.tv, state.tvConnection)
            OutlinedButton(onClick = onConfigureLg, modifier = Modifier.fillMaxWidth().testTag("configure_lg")) {
                Text(stringResource(R.string.configure_lg))
            }
            StatusLine(R.string.streamer, state.streamerConnection)
            OutlinedButton(onClick = onConfigureXiaomi, modifier = Modifier.fillMaxWidth().testTag("configure_xiaomi")) {
                Text(stringResource(R.string.configure_xiaomi))
            }
            StatusLine(R.string.soundbar, state.soundbarConnection)
        }
    }
}

@Composable
private fun StatusLine(@StringRes deviceName: Int, connection: ConnectionState) {
    val connectionLabel = when (connection) {
        ConnectionState.NOT_CONFIGURED -> R.string.not_configured
        ConnectionState.DISCOVERING -> R.string.discovering
        ConnectionState.PAIRING -> R.string.pairing
        ConnectionState.WAITING_FOR_CODE -> R.string.waiting_for_code
        ConnectionState.CONNECTING -> R.string.connecting
        ConnectionState.CONNECTED -> R.string.connected
        ConnectionState.DISCONNECTED -> R.string.disconnected
        ConnectionState.ERROR -> R.string.connection_error
        ConnectionState.SIMULATED -> R.string.simulated
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(deviceName), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(connectionLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun SourceButton(
    source: InputSource,
    @StringRes label: Int,
    state: RemoteState,
    onAction: (RemoteAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    RemoteButton(
        label = stringResource(label),
        tag = "source_${source.name.lowercase()}",
        modifier = modifier,
        emphasized = state.selectedInput == source,
        onClick = { onAction(RemoteAction.SelectInput(source)) },
    )
}

@Composable
private fun NumberPad(onAction: (RemoteAction) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { digit ->
                    RemoteButton(digit.toString(), "digit_$digit", Modifier.weight(1f)) {
                        onAction(RemoteAction.NumericKey(digit))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            RemoteButton("0", "digit_0", Modifier.weight(1f)) {
                onAction(RemoteAction.NumericKey(0))
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun DirectionPad(onAction: (RemoteAction) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DirectionButton("↑", R.string.up, "up") { onAction(RemoteAction.Key(RemoteKey.UP)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DirectionButton("←", R.string.left, "left") { onAction(RemoteAction.Key(RemoteKey.LEFT)) }
                DirectionButton("OK", R.string.ok, "ok", emphasized = true) {
                    onAction(RemoteAction.Key(RemoteKey.CENTER))
                }
                DirectionButton("→", R.string.right, "right") { onAction(RemoteAction.Key(RemoteKey.RIGHT)) }
            }
            DirectionButton("↓", R.string.down, "down") { onAction(RemoteAction.Key(RemoteKey.DOWN)) }
        }
    }
}

@Composable
private fun DirectionButton(
    symbol: String,
    @StringRes labelId: Int,
    tag: String,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val label = stringResource(labelId)
    val modifier = Modifier.size(68.dp).testTag(tag).semantics { contentDescription = label }
    if (emphasized) {
        Button(onClick = onClick, modifier = modifier, contentPadding = ButtonDefaults.ContentPadding) {
            Text(symbol, textAlign = TextAlign.Center)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = ButtonDefaults.ContentPadding) {
            Text(symbol, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RemoteButton(
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val buttonModifier = modifier.height(58.dp).testTag(tag)
    if (emphasized) {
        Button(onClick = onClick, modifier = buttonModifier, contentPadding = ButtonDefaults.ContentPadding) {
            Text(label, textAlign = TextAlign.Center, maxLines = 2)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = buttonModifier, contentPadding = ButtonDefaults.ContentPadding) {
            Text(label, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
