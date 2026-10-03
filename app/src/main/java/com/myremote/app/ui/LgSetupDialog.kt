package com.myremote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.myremote.app.R
import com.myremote.app.domain.ConnectionState
import com.myremote.app.lg.LgDevice
import com.myremote.app.lg.normalizedLgHost

@Composable
fun LgSetupDialog(
    devices: List<LgDevice>,
    connection: ConnectionState,
    error: String?,
    onDismiss: () -> Unit,
    onDevice: (LgDevice) -> Unit,
    onManualHost: (String) -> Unit,
    onRetry: () -> Unit,
    onForget: () -> Unit,
) {
    var host by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.lg_setup), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.lg_pairing_guidance), style = MaterialTheme.typography.bodyMedium)
                if (connection == ConnectionState.PAIRING) {
                    Text(stringResource(R.string.lg_approve_on_tv), color = MaterialTheme.colorScheme.primary)
                }
                if (connection == ConnectionState.CONNECTED) {
                    Text(stringResource(R.string.lg_connected), color = MaterialTheme.colorScheme.primary)
                }
                if (connection != ConnectionState.CONNECTED) {
                    Text(stringResource(R.string.discovered_devices), style = MaterialTheme.typography.titleMedium)
                    if (devices.isEmpty()) Text(stringResource(R.string.searching_devices))
                    devices.forEach { device ->
                        OutlinedButton(onClick = { onDevice(device) }, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(device.name)
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(listOfNotNull(device.model, device.host).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(value = host, onValueChange = { host = it },
                            label = { Text(stringResource(R.string.manual_host)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("lg_manual_host"))
                    }
                    Button(onClick = { onManualHost(host) }, enabled = normalizedLgHost(host) != null,
                        modifier = Modifier.fillMaxWidth().testTag("lg_connect_host")) {
                        Text(stringResource(R.string.connect_host))
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.retry))
                    }
                    OutlinedButton(onClick = onForget, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.forget_pairing))
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}
