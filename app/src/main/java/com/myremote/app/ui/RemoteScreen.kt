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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.core.text.BidiFormatter
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
    val snackbar = remember { SnackbarHostState() }
    val feedback = failureText(state.failure)
    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) snackbar.showSnackbar(feedback)
    }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var helpVisible by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Header(state, { settingsVisible = true }, { onAction(RemoteAction.Power) })
            DeviceCards(state, { settingsVisible = true }, { settingsVisible = true }, { settingsVisible = true })
            if (state.errorMessage != null) {
                Text(failureText(state.failure), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("action_error").semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (state.busyDevices.isNotEmpty()) Text(stringResource(R.string.sending_command),
                color = MutedInk, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (connectionSessionActive == false) {
                Text(stringResource(R.string.remote_paused_guidance), color = MutedInk, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onConnectionSessionToggle, modifier = Modifier.testTag("resume_session")) {
                    Text(stringResource(R.string.connect_remote))
                }
            }
            Panel {
                SectionTitle(R.string.sources)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val sources = listOf(Triple(InputSource.PS5, R.string.ps5, RemoteGlyph.GAMEPAD),
                        Triple(InputSource.MAC_MINI, R.string.mac_mini, RemoteGlyph.LAPTOP),
                        Triple(InputSource.XIAOMI, R.string.device_xiaomi_short, RemoteGlyph.BOX),
                        Triple(InputSource.PC, R.string.pc, RemoteGlyph.PC))
                    sources.chunked(if (LocalDensity.current.fontScale > 1.3f) 2 else 4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEach { (source, label, glyph) -> SourceButton(source, label, glyph, state, onAction, Modifier.weight(1f)) }
                        }
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
            if (state.selectedInput == InputSource.XIAOMI) {
                Panel {
                    SectionTitle(R.string.navigation)
                    NavigationPad(onAction)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RemoteTile(stringResource(R.string.rewind), "rewind", Modifier.weight(1f).heightIn(min = 56.dp), glyph = RemoteGlyph.REWIND, iconOnly = true) { onAction(RemoteAction.Key(RemoteKey.REWIND)) }
                            RemoteTile(stringResource(R.string.play_pause), "play_pause", Modifier.weight(1f).heightIn(min = 56.dp), glyph = RemoteGlyph.PLAY_PAUSE, iconOnly = true) { onAction(RemoteAction.Key(RemoteKey.PLAY_PAUSE)) }
                            RemoteTile(stringResource(R.string.fast_forward), "fast_forward", Modifier.weight(1f).heightIn(min = 56.dp), glyph = RemoteGlyph.FORWARD, iconOnly = true) { onAction(RemoteAction.Key(RemoteKey.FAST_FORWARD)) }
                        }
                    }
                }
                Panel {
                    SectionTitle(R.string.channels)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RemoteTile(stringResource(R.string.channel_down), "channel_down", Modifier.weight(1f).heightIn(min = 72.dp), glyph = RemoteGlyph.MINUS) { onAction(RemoteAction.ChannelDown) }
                            RemoteTile(stringResource(R.string.last_channel), "last_channel", Modifier.weight(1f).heightIn(min = 72.dp), glyph = RemoteGlyph.BACK) { onAction(RemoteAction.LastChannel) }
                            RemoteTile(stringResource(R.string.channel_up), "channel_up", Modifier.weight(1f).heightIn(min = 72.dp), glyph = RemoteGlyph.PLUS) { onAction(RemoteAction.ChannelUp) }
                        }
                    }
                    NumberPad(onAction)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
        }
    }
    if (settingsVisible) AlertDialog(
        onDismissRequest = { settingsVisible = false }, title = { Text(stringResource(R.string.remote_settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { settingsVisible = false; onConfigureLg() }) { Text(stringResource(R.string.configure_lg)) }
                TextButton(onClick = { settingsVisible = false; onConfigureXiaomi() }) { Text(stringResource(R.string.configure_xiaomi)) }
                TextButton(onClick = { settingsVisible = false; onConfigureSamsung() }) { Text(stringResource(R.string.samsung_setup)) }
                TextButton(onClick = { settingsVisible = false; helpVisible = true }) { Text(stringResource(R.string.remote_help)) }
                TextButton(onClick = { onAction(RemoteAction.TvPower) }, modifier = Modifier.testTag("lg_power")) { Text(stringResource(R.string.tv_power)) }
                TextButton(onClick = { onAction(RemoteAction.StreamerOff) }, modifier = Modifier.testTag("xiaomi_off")) { Text(stringResource(R.string.streamer_off)) }
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
private fun Header(state: RemoteState, onSettings: () -> Unit, onPower: () -> Unit) {
    val powerLabel = stringResource(if (state.activeDevice == ActiveDevice.TV) R.string.tv_power else R.string.streamer_power)
    val activeDescription = stringResource(R.string.active_device,
        BidiFormatter.getInstance(LocalLayoutDirection.current == LayoutDirection.Rtl).unicodeWrap(stringResource(if (state.activeDevice == ActiveDevice.TV) R.string.tv else R.string.streamer)))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RemoteTile(stringResource(R.string.remote_settings), "remote_settings", Modifier.size(48.dp), glyph = RemoteGlyph.SETTINGS, iconOnly = true, flat = true, onClick = onSettings)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.remote_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(activeDescription, fontSize = 14.sp, color = MutedInk, textAlign = TextAlign.Center, modifier = Modifier.testTag("active_device").semantics { contentDescription = activeDescription })
            }
            RemoteTile(powerLabel, "power", Modifier.size(48.dp), glyph = RemoteGlyph.POWER, iconOnly = true, emphasized = true, onClick = onPower)
        }

}

@Composable
private fun DeviceCards(state: RemoteState, lg: () -> Unit, xiaomi: () -> Unit, samsung: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DeviceCard(R.string.device_lg_short, state.tvConnection, "configure_lg", Modifier, lg)
        DeviceCard(R.string.device_xiaomi_short, state.streamerConnection, "configure_xiaomi", Modifier, xiaomi)
        DeviceCard(R.string.device_samsung_short, state.soundbarConnection, "configure_samsung", Modifier, samsung)
    }
}

@Composable
private fun DeviceCard(@StringRes name: Int, state: ConnectionState, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val label = stringResource(connectionLabel(state))
    val nameLabel = BidiFormatter.getInstance(LocalLayoutDirection.current == LayoutDirection.Rtl).unicodeWrap(stringResource(name))
    val description = "$nameLabel: $label"
    Row(modifier.heightIn(min = 48.dp).clip(TileShape).background(Color(0xFF16212F))
        .clickable(role = Role.Button, onClick = onClick).testTag(tag)
        .semantics(mergeDescendants = true) { contentDescription = description; stateDescription = label; liveRegion = LiveRegionMode.Polite }
        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusDot(state)
        Text(description, color = Ink, fontSize = 13.sp)
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
    ConnectionState.NOT_CONFIGURED -> R.string.needs_connection
    ConnectionState.DISCOVERING -> R.string.discovering
    ConnectionState.PAIRING -> R.string.pairing
    ConnectionState.WAITING_FOR_CODE -> R.string.connecting
    ConnectionState.CONNECTING -> R.string.connecting
    ConnectionState.CONNECTED -> R.string.connected
    ConnectionState.DISCONNECTED -> R.string.disconnected
    ConnectionState.ERROR -> R.string.connection_error
    ConnectionState.AUTHORIZATION_REQUIRED -> R.string.needs_connection
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
    Text(BidiFormatter.getInstance(LocalLayoutDirection.current == LayoutDirection.Rtl).unicodeWrap(stringResource(title)), modifier.fillMaxWidth(), color = MutedInk, fontSize = 13.sp,
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
            Text(label, color = tint, fontSize = if (glyph == null && icon == null) 21.sp else 14.sp,
                textAlign = TextAlign.Center,
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
            val padSize = (if (LocalDensity.current.fontScale > 1.3f) 224.dp else 192.dp).coerceAtMost(maxWidth)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteTile(stringResource(R.string.home), "home", Modifier.weight(1f).heightIn(min = 56.dp), glyph = RemoteGlyph.HOME) { onAction(RemoteAction.Key(RemoteKey.HOME)) }
                RemoteTile(stringResource(R.string.back), "back", Modifier.weight(1f).heightIn(min = 56.dp), glyph = RemoteGlyph.BACK) { onAction(RemoteAction.Key(RemoteKey.BACK)) }
                }
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
                        Text(ok, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clearAndSetSemantics { })
                    }
                }

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
