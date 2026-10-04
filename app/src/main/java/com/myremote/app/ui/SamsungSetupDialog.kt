package com.myremote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.myremote.app.R
import com.myremote.app.domain.ConnectionState
import com.myremote.app.domain.FailureKind
import com.myremote.app.samsung.SamsungDevice

@Composable fun SamsungSetupDialog(
    devices: List<SamsungDevice>, connection: ConnectionState, error: FailureKind?, hasPermission: Boolean,
    onPermission: () -> Unit, onDevice: (SamsungDevice) -> Unit, onRetry: () -> Unit,
    onSettings: () -> Unit, onForget: () -> Unit, onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.samsung_setup), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.samsung_guidance))
                if (!hasPermission) OutlinedButton(onClick = onPermission, modifier = Modifier.fillMaxWidth().testTag("samsung_permission")) {
                    Text(stringResource(R.string.samsung_allow))
                } else {
                    if (devices.isEmpty()) Text(stringResource(R.string.samsung_no_devices))
                    devices.forEach { device ->
                        OutlinedButton(onClick = { onDevice(device) }, modifier = Modifier.fillMaxWidth().testTag("samsung_device")) {
                            Text(device.name)
                        }
                    }
                    if (connection == ConnectionState.CONNECTED) Text(stringResource(R.string.connected))
                    if (connection == ConnectionState.CONNECTING) Text(stringResource(R.string.connecting))
                    error?.let { Text(failureText(it), color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.retry)) }
                }
                OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bluetooth_settings)) }
                OutlinedButton(onClick = onForget, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.forget_pairing)) }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
            }
        }
    }
}
