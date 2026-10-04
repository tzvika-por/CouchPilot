package com.myremote.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myremote.app.R
import com.myremote.app.domain.*

private val Accent = Color(0xFF36C9FF)
private val Ink = Color(0xFFE4ECFF)
private val MutedInk = Color(0xFFADBBD3)
private val Online = Color(0xFF43ED83)
private val TileShape = RoundedCornerShape(14.dp)

@Composable
fun RemoteScreen(state: RemoteState, onAction: (RemoteAction) -> Unit,
    onConfigureXiaomi: () -> Unit = {}, onConfigureLg: () -> Unit = {}, onConfigureSamsung: () -> Unit = {},
    connectionSessionActive: Boolean? = null, onConnectionSessionToggle: () -> Unit = {}) {
    val soundbarName = stringResource(R.string.soundbar)
    var settingsVisible by remember { mutableStateOf(false) }
    var helpVisible by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Header(state, { settingsVisible = true }, { helpVisible = true }, { onAction(RemoteAction.Power) })
            DeviceCards(state, onConfigureLg, onConfigureXiaomi, onConfigureSamsung)
            if (connectionSessionActive == false) {
                Text(stringResource(R.string.remote_paused_guidance), color = MutedInk, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onConnectionSessionToggle, modifier = Modifier.testTag("resume_session")) {
                    Text(stringResource(R.string.connect_remote))
                }
            }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(R.string.sources, Modifier.weight(1f))
                    TextButton(onClick = { onAction(RemoteAction.StreamerOff) }, modifier = Modifier.testTag("xiaomi_off")) {
                        RemoteGlyph(RemoteGlyph.POWER, MutedInk, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.streamer_off), fontSize = 12.sp)
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SourceButton(InputSource.PS5, R.string.ps5, RemoteGlyph.GAMEPAD, state, onAction, Modifier.weight(1f))
                        SourceButton(InputSource.MAC_MINI, R.string.mac_mini, RemoteGlyph.LAPTOP, state, onAction, Modifier.weight(1f))
                        SourceButton(InputSource.XIAOMI, R.string.device_xiaomi_short, RemoteGlyph.BOX, state, onAction, Modifier.weight(1f))
                        SourceButton(InputSource.PC, R.string.pc, RemoteGlyph.PC, state, onAction, Modifier.weight(1f))
                    }
                }
            }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(R.string.sound, Modifier.weight(1f))
                    RemoteGlyph(RemoteGlyph.SOUNDBAR, MutedInk, Modifier.size(22.dp).semantics { contentDescription = soundbarName })
                    Spacer(Modifier.width(6.dp))
                    StatusDot(state.soundbarConnection)
                    Spacer(Modifier.width(4.dp))
                    RemoteTile(stringResource(R.string.soundbar_power), "soundbar_power", Modifier.size(48.dp),
                        glyph = RemoteGlyph.POWER, iconOnly = true) { onAction(RemoteAction.SoundbarPower) }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteTile(stringResource(R.string.volume_down), "volume_down", Modifier.weight(1f).height(60.dp),
                            icon = R.drawable.ic_volume_down, iconOnly = true) { onAction(RemoteAction.VolumeDown) }
                        RemoteTile(stringResource(when (state.soundbarMuted) {
                            true -> R.string.unmute; false -> R.string.mute; null -> R.string.mute_toggle
                        }), "mute", Modifier.weight(1f).height(60.dp),
                            icon = if (state.soundbarMuted == true) R.drawable.ic_volume_up else R.drawable.ic_volume_off,
                            iconOnly = true, tint = if (state.soundbarMuted == true) Color(0xFFFF7293) else Ink) { onAction(RemoteAction.Mute) }
                        RemoteTile(stringResource(R.string.volume_up), "volume_up", Modifier.weight(1f).height(60.dp),
                            icon = R.drawable.ic_volume_up, iconOnly = true) { onAction(RemoteAction.VolumeUp) }
                    }
                }
            }
            Panel {
                SectionTitle(R.string.channels)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteTile(stringResource(R.string.last_channel), "last_channel", Modifier.weight(1f).heightIn(min = 72.dp), glyph = RemoteGlyph.BACK) { onAction(RemoteAction.LastChannel) }
                        RemoteTile(stringResource(R.string.channel_down), "channel_down", Modifier.weight(1f).heightIn(min = 72.dp), glyph = RemoteGlyph.MINUS) { onAction(RemoteAction.ChannelDown) }
                        RemoteTile(stringResource(R.string.channel_up), "channel_up", Modifier.weight(1f).heightIn(min = 72.dp), glyph = RemoteGlyph.PLUS) { onAction(RemoteAction.ChannelUp) }
                    }
                }
            }
            Panel {
                SectionTitle(R.string.number_pad)
                NumberPad(onAction)
            }
            Panel {
                SectionTitle(R.string.navigation)
                NavigationPad(onAction)
            }
            Panel {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteTile(stringResource(R.string.rewind), "rewind", Modifier.weight(1f).height(56.dp), glyph = RemoteGlyph.REWIND, iconOnly = true) { onAction(RemoteAction.Key(RemoteKey.REWIND)) }
                        RemoteTile(stringResource(R.string.play_pause), "play_pause", Modifier.weight(1f).height(56.dp), glyph = RemoteGlyph.PLAY_PAUSE, iconOnly = true) { onAction(RemoteAction.Key(RemoteKey.PLAY_PAUSE)) }
                        RemoteTile(stringResource(R.string.fast_forward), "fast_forward", Modifier.weight(1f).height(56.dp), glyph = RemoteGlyph.FORWARD, iconOnly = true) { onAction(RemoteAction.Key(RemoteKey.FAST_FORWARD)) }
                    }
                }
            }
            state.errorMessage?.let {
                Text(failureText(state.failure), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("action_error"))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (settingsVisible) AlertDialog(
        onDismissRequest = { settingsVisible = false }, title = { Text(stringResource(R.string.remote_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { settingsVisible = false; onConfigureLg() }) { Text(stringResource(R.string.configure_lg)) }
                TextButton(onClick = { settingsVisible = false; onConfigureXiaomi() }) { Text(stringResource(R.string.configure_xiaomi)) }
                TextButton(onClick = { settingsVisible = false; onConfigureSamsung() }) { Text(stringResource(R.string.samsung_setup)) }
                connectionSessionActive?.let { active ->
                    HorizontalDivider()
                    Text(stringResource(if (active) R.string.remote_background_guidance else R.string.remote_paused_guidance), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onConnectionSessionToggle, modifier = Modifier.testTag("connection_session")) {
                        Text(stringResource(if (active) R.string.disconnect_remote else R.string.connect_remote))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { settingsVisible = false }) { Text(stringResource(R.string.close)) } },
    )
    if (helpVisible) AlertDialog(onDismissRequest = { helpVisible = false },
        title = { Text(stringResource(R.string.remote_help)) },
        text = { Text(stringResource(R.string.remote_help_text)) },
        confirmButton = { TextButton(onClick = { helpVisible = false }) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun Header(state: RemoteState, onSettings: () -> Unit, onHelp: () -> Unit, onPower: () -> Unit) {
    val powerLabel = stringResource(if (state.activeDevice == ActiveDevice.TV) R.string.tv_power else R.string.streamer_power)
    val activeDescription = stringResource(R.string.active_device,
        stringResource(if (state.activeDevice == ActiveDevice.TV) R.string.tv else R.string.streamer))
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RemoteTile(stringResource(R.string.remote_settings), "remote_settings", Modifier.size(48.dp), glyph = RemoteGlyph.SETTINGS, iconOnly = true, flat = true, onClick = onSettings)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.remote_title), fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(powerLabel, fontSize = 10.sp, color = MutedInk, textAlign = TextAlign.Center, modifier = Modifier.testTag("active_device").semantics { contentDescription = activeDescription })
            }
            RemoteTile(powerLabel, "power", Modifier.size(48.dp), glyph = RemoteGlyph.POWER, iconOnly = true, emphasized = true, onClick = onPower)
            RemoteTile(stringResource(R.string.remote_help), "remote_help", Modifier.size(48.dp), glyph = RemoteGlyph.HELP, iconOnly = true, flat = true, onClick = onHelp)
        }
    }
}

@Composable
private fun DeviceCards(state: RemoteState, lg: () -> Unit, xiaomi: () -> Unit, samsung: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            DeviceCard(R.string.device_lg_short, R.string.configure_lg, RemoteGlyph.TV, state.tvConnection, "configure_lg", Modifier.weight(1f), lg)
            DeviceCard(R.string.device_xiaomi_short, R.string.configure_xiaomi, RemoteGlyph.BOX, state.streamerConnection, "configure_xiaomi", Modifier.weight(1f), xiaomi)
            DeviceCard(R.string.device_samsung_short, R.string.samsung_setup, RemoteGlyph.SOUNDBAR, state.soundbarConnection, "configure_samsung", Modifier.weight(1f), samsung)
        }
    }
}

@Composable
private fun DeviceCard(@StringRes name: Int, @StringRes setup: Int, glyph: RemoteGlyph,
    state: ConnectionState, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val description = stringResource(setup)
    Row(modifier.heightIn(min = 76.dp).clip(TileShape).background(Color(0xFF16212F))
        .clickable(role = Role.Button, onClick = onClick).testTag(tag).semantics(mergeDescendants = true) { contentDescription = description }
        .padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RemoteGlyph(glyph, MutedInk, Modifier.size(25.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(name), color = Ink, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusDot(state)
                Text(stringResource(connectionLabel(state)), fontSize = 10.sp, lineHeight = 12.sp,
                    color = MutedInk, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.CONNECTED -> Online
        ConnectionState.CONNECTING, ConnectionState.PAIRING, ConnectionState.DISCOVERING, ConnectionState.WAITING_FOR_CODE -> Color(0xFFFFC56E)
        ConnectionState.ERROR, ConnectionState.AUTHORIZATION_REQUIRED -> Color(0xFFFF7293)
        else -> Color(0xFF78869B)
    }
    Box(Modifier.size(6.dp).background(color, CircleShape))
}

@StringRes
private fun connectionLabel(state: ConnectionState) = when (state) {
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

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF101D2A), Color(0xFF0D1722))))
        .padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun SectionTitle(@StringRes title: Int, modifier: Modifier = Modifier) {
    Text(stringResource(title), modifier.fillMaxWidth(), color = MutedInk, fontSize = 13.sp,
        fontWeight = FontWeight.Medium, textAlign = TextAlign.Start)
}

@Composable
private fun SourceButton(source: InputSource, @StringRes label: Int, glyph: RemoteGlyph,
    state: RemoteState, onAction: (RemoteAction) -> Unit, modifier: Modifier) {
    RemoteTile(stringResource(label), "source_${source.name.lowercase()}", modifier.heightIn(min = 76.dp),
        glyph = glyph, emphasized = source == state.selectedInput, showSelection = true) {
        onAction(RemoteAction.SelectInput(source))
    }
}

@Composable
private fun RemoteTile(label: String, tag: String, modifier: Modifier, glyph: RemoteGlyph? = null,
    @DrawableRes icon: Int? = null, iconOnly: Boolean = false, emphasized: Boolean = false,
    showSelection: Boolean = false, flat: Boolean = false, tint: Color = Ink, onClick: () -> Unit) {
    val shape = TileShape
    val tile = modifier.then(if (emphasized) Modifier.shadow(10.dp, shape, ambientColor = Accent, spotColor = Accent) else Modifier)
        .clip(shape).then(if (flat) Modifier else Modifier.background(Brush.verticalGradient(
            if (emphasized) listOf(Color(0xFF073D76), Color(0xFF082253)) else listOf(Color(0xFF253343), Color(0xFF192431)))))
        .then(if (flat) Modifier else Modifier.border(BorderStroke(if (emphasized) 1.5.dp else .8.dp,
            if (emphasized) Accent else Color(0xFF354251)), shape))
        .clickable(role = Role.Button, onClick = onClick).testTag(tag)
        .semantics(mergeDescendants = true) { contentDescription = label; if (showSelection) selected = emphasized }
    Column(tile.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (glyph != null) RemoteGlyph(glyph, if (emphasized) Accent else tint, Modifier.size(if (iconOnly) 27.dp else 29.dp))
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(29.dp), tint = tint)
        if (!iconOnly) {
            if (glyph != null || icon != null) Spacer(Modifier.height(4.dp))
            Text(label, color = tint, fontSize = if (glyph == null && icon == null) 21.sp else 12.sp,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { })
        }
        if (showSelection) {
            Spacer(Modifier.height(3.dp))
            Box(Modifier.size(4.dp).background(if (emphasized) Accent else Color.Transparent, CircleShape))
        }
    }
}

@Composable
private fun NumberPad(onAction: (RemoteAction) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(0)).forEach { digits ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (digits.size == 1) Spacer(Modifier.weight(1f))
                    digits.forEach { digit -> RemoteTile(digit.toString(), "digit_$digit", Modifier.weight(1f).heightIn(min = 48.dp)) { onAction(RemoteAction.NumericKey(digit)) } }
                    if (digits.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NavigationPad(onAction: (RemoteAction) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val padSize = (maxWidth * .56f).coerceIn(156.dp, 180.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteTile(stringResource(R.string.home), "home", Modifier.weight(1f).heightIn(min = 84.dp), glyph = RemoteGlyph.HOME) { onAction(RemoteAction.Key(RemoteKey.HOME)) }
                Box(Modifier.size(padSize)) {
                    Canvas(Modifier.matchParentSize()) {
                        val inset = size.width * .28f
                        for (angle in listOf(225f, 315f, 45f, 135f)) {
                            val wedge = Path().apply {
                                arcTo(Rect(1f, 1f, size.width - 1f, size.height - 1f), angle + 2f, 86f, true)
                                arcTo(Rect(inset, inset, size.width - inset, size.height - inset), angle + 88f, -86f, false)
                                close()
                            }
                            drawPath(wedge, Brush.verticalGradient(listOf(Color(0xFF2A3B50), Color(0xFF182535))))
                            drawPath(wedge, Color(0xFF3C4D65), style = Stroke(1f))
                        }
                    }
                    val directionSize = (padSize * .31f).coerceAtLeast(48.dp)
                    DirectionButton(RemoteGlyph.UP, R.string.up, "up", Modifier.align(Alignment.TopCenter).size(directionSize)) { onAction(RemoteAction.Key(RemoteKey.UP)) }
                    DirectionButton(RemoteGlyph.DOWN, R.string.down, "down", Modifier.align(Alignment.BottomCenter).size(directionSize)) { onAction(RemoteAction.Key(RemoteKey.DOWN)) }
                    DirectionButton(RemoteGlyph.LEFT, R.string.left, "left", Modifier.align(Alignment.CenterStart).size(directionSize)) { onAction(RemoteAction.Key(RemoteKey.LEFT)) }
                    DirectionButton(RemoteGlyph.RIGHT, R.string.right, "right", Modifier.align(Alignment.CenterEnd).size(directionSize)) { onAction(RemoteAction.Key(RemoteKey.RIGHT)) }
                    val ok = stringResource(R.string.ok)
                    Box(Modifier.align(Alignment.Center).size(padSize * .35f).shadow(6.dp, CircleShape).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF283C52), Color(0xFF142334))))
                        .border(1.dp, Color(0xFF405571), CircleShape).clickable(role = Role.Button) { onAction(RemoteAction.Key(RemoteKey.CENTER)) }
                        .testTag("ok").semantics { contentDescription = ok }, contentAlignment = Alignment.Center) {
                        Text("OK", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clearAndSetSemantics { })
                    }
                }
                RemoteTile(stringResource(R.string.back), "back", Modifier.weight(1f).heightIn(min = 84.dp), glyph = RemoteGlyph.BACK) { onAction(RemoteAction.Key(RemoteKey.BACK)) }
            }
        }
    }
}

@Composable
private fun DirectionButton(glyph: RemoteGlyph, @StringRes label: Int, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val description = stringResource(label)
    Box(modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
        .testTag(tag).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        RemoteGlyph(glyph, Ink, Modifier.size(28.dp))
    }
}
