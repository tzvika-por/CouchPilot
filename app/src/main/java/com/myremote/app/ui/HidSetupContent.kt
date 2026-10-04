package com.myremote.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.myremote.app.R
import com.myremote.app.domain.FailureKind
import com.myremote.app.hid.HidHost

@Composable
fun HidSetupContent(
    hosts: List<HidHost>, hasPermission: Boolean, supported: Boolean, registered: Boolean,
    pairing: Boolean, error: FailureKind?, onPermission: () -> Unit,
    onPair: () -> Unit, onHost: (HidHost) -> Unit, onSettings: () -> Unit = {},
) {
    Text(stringResource(R.string.bluetooth_remote_guidance))
    Text(stringResource(R.string.bluetooth_host_note))
    if (!supported) Text(stringResource(R.string.bluetooth_remote_unsupported))
    else if (!hasPermission) Button(onClick = onPermission, modifier = Modifier.fillMaxWidth().testTag("hid_permission")) {
        Text(stringResource(R.string.samsung_allow))
    } else {
        Text(stringResource(R.string.bluetooth_tv_pair_steps))
        Button(onClick = onPair, enabled = registered && !pairing, modifier = Modifier.fillMaxWidth().testTag("hid_pair")) {
            Text(stringResource(if (pairing) R.string.bluetooth_pairing_started else R.string.bluetooth_pair_xiaomi))
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
