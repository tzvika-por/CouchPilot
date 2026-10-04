package com.myremote.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.safeDrawingPadding
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
    onConfigureXiaomi: () -> Unit = {}, onConfigureLg: () -> Unit = {}, onConfigureSamsung: () -> Unit = {}) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.remote_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            StatusPanel(state, onConfigureXiaomi, onConfigureLg, onConfigureSamsung)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoteButton(
                    label = stringResource(if (state.activeDevice == ActiveDevice.STREAMER)
                        R.string.streamer_power else R.string.tv_power),
                    tag = "power",
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(RemoteAction.Power) },
                )
                RemoteButton(
                    label = stringResource(R.string.streamer_off),
                    tag = "xiaomi_off",
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(RemoteAction.StreamerOff) },
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
            RemoteButton(stringResource(R.string.soundbar_power), "soundbar_power", Modifier.fillMaxWidth()) {
                onAction(RemoteAction.SoundbarPower)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoteButton(stringResource(R.string.volume_down), "volume_down", Modifier.weight(1f), icon = R.drawable.ic_volume_down) {
                    onAction(RemoteAction.VolumeDown)
                }
                RemoteButton(
                    stringResource(when (state.soundbarMuted) {
                        true -> R.string.unmute
                        false -> R.string.mute
                        null -> R.string.mute_toggle
                    }), "mute", Modifier.weight(1f),
                    icon = if (state.soundbarMuted == true) R.drawable.ic_volume_up else R.drawable.ic_volume_off,
                ) {
                    onAction(RemoteAction.Mute)
                }
                RemoteButton(stringResource(R.string.volume_up), "volume_up", Modifier.weight(1f), icon = R.drawable.ic_volume_up) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoteButton(stringResource(R.string.rewind), "rewind", Modifier.weight(1f)) {
                    onAction(RemoteAction.Key(RemoteKey.REWIND))
                }
                RemoteButton(stringResource(R.string.fast_forward), "fast_forward", Modifier.weight(1f)) {
                    onAction(RemoteAction.Key(RemoteKey.FAST_FORWARD))
                }
            }
            state.errorMessage?.let {
                Text(failureText(state.failure), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("action_error"))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatusPanel(state: RemoteState, onConfigureXiaomi: () -> Unit, onConfigureLg: () -> Unit, onConfigureSamsung: () -> Unit) {
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
            OutlinedButton(onClick = onConfigureSamsung, modifier = Modifier.fillMaxWidth().testTag("configure_samsung")) {
                Text(stringResource(R.string.samsung_setup))
            }
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
        ConnectionState.AUTHORIZATION_REQUIRED -> R.string.lg_authorization_required
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
    @DrawableRes icon: Int? = null,
    onClick: () -> Unit,
) {
    val buttonModifier = modifier.height(58.dp).testTag(tag)
    if (emphasized) {
        Button(onClick = onClick, modifier = buttonModifier, contentPadding = ButtonDefaults.ContentPadding) {
            RemoteButtonContent(label, icon)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = buttonModifier, contentPadding = ButtonDefaults.ContentPadding) {
            RemoteButtonContent(label, icon)
        }
    }
}

@Composable
private fun RemoteButtonContent(label: String, @DrawableRes icon: Int?) {
    if (icon != null) {
        Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(28.dp))
    } else {
        Text(label, textAlign = TextAlign.Center, maxLines = 2)
    }
}
