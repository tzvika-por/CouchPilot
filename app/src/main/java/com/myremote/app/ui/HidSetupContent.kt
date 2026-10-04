package com.myremote.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.myremote.app.R
import com.myremote.app.domain.FailureKind
import com.myremote.app.domain.ConnectionState
import com.myremote.app.hid.HidHost

@Composable
fun HidSetupContent(
    hosts: List<HidHost>, hasPermission: Boolean, supported: Boolean, registered: Boolean,
    pairing: Boolean, error: FailureKind?, onPermission: () -> Unit,
    onPair: () -> Unit, onHost: (HidHost) -> Unit, onSettings: () -> Unit = {},
    bonded: Boolean = false, connection: ConnectionState = ConnectionState.DISCONNECTED,
) {
    var address by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.bluetooth_remote_guidance))
    Text(stringResource(R.string.bluetooth_host_note))
    if (!supported) Text(stringResource(R.string.bluetooth_remote_unsupported))
    else if (!hasPermission) Button(onClick = onPermission, modifier = Modifier.fillMaxWidth().testTag("hid_permission")) {
        Text(stringResource(R.string.samsung_allow))
    } else {
        Text(stringResource(if (bonded) R.string.bluetooth_saved_bond else R.string.bluetooth_tv_pair_steps))
        Button(onClick = onPair, enabled = registered && !pairing && connection == ConnectionState.DISCONNECTED, modifier = Modifier.fillMaxWidth().testTag("hid_pair")) {
            Text(stringResource(when {
                connection == ConnectionState.CONNECTED -> R.string.xiaomi_connected
                pairing -> R.string.bluetooth_pairing_started
                bonded -> R.string.retry
                else -> R.string.bluetooth_pair_xiaomi
            }))
        }
        if (hosts.isEmpty() && !registered) {
            Text(stringResource(R.string.hid_manual_guidance))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(value = address, onValueChange = { address = it.take(17) },
                    label = { Text(stringResource(R.string.hid_address)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("hid_manual_address"))
            }
            Button(onClick = { onHost(HidHost("Xiaomi", address.uppercase())) },
                enabled = Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}").matches(address),
                modifier = Modifier.fillMaxWidth().testTag("hid_select_manual")) {
                Text(stringResource(R.string.configure_xiaomi))
            }
        }
        hosts.forEach { host ->
            OutlinedButton(onClick = { onHost(host) }, modifier = Modifier.fillMaxWidth().testTag("hid_host")) {
                Text(host.name)
            }
        }
    }
    if (hasPermission && supported) {
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().testTag("hid_settings")) {
            Text(stringResource(R.string.bluetooth_settings))
        }
    }
    if (error != null && (hasPermission || error != FailureKind.PERMISSION_DENIED)) {
        Text(if (error == FailureKind.UNAVAILABLE) stringResource(R.string.bluetooth_remote_unavailable) else failureText(error))
    }
}
