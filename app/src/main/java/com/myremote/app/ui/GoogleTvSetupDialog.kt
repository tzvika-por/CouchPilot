package com.myremote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.myremote.app.R
import com.myremote.app.domain.ConnectionState
import com.myremote.app.google.GoogleTvDevice
import com.myremote.app.google.normalizedGoogleTvHost

@Composable
fun GoogleTvSetupDialog(
    devices: List<GoogleTvDevice>,
    connection: ConnectionState,
    error: String?,
    onDismiss: () -> Unit,
    onDevice: (GoogleTvDevice) -> Unit,
    onManualHost: (String) -> Unit,
    onCode: (String) -> Unit,
    onRetry: () -> Unit,
    onForget: () -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.xiaomi_setup), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.pairing_guidance), style = MaterialTheme.typography.bodyMedium)
                if (connection == ConnectionState.WAITING_FOR_CODE) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 6) code = it.uppercase() },
                        label = { Text(stringResource(R.string.pairing_code)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pairing_code"),
                    )
                    Button(
                        onClick = { onCode(code) },
                        enabled = Regex("[0-9A-Fa-f]{6}").matches(code),
                        modifier = Modifier.fillMaxWidth().testTag("submit_pairing_code"),
                    ) { Text(stringResource(R.string.pair)) }
                } else {
                    Text(stringResource(R.string.discovered_devices), style = MaterialTheme.typography.titleMedium)
                    if (devices.isEmpty()) Text(stringResource(R.string.searching_devices))
                    devices.forEach { device ->
                        OutlinedButton(onClick = { onDevice(device) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${device.name} · ${device.host}")
                        }
                    }
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(stringResource(R.string.manual_host)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("manual_host"),
                    )
                    Button(
                        onClick = { onManualHost(host) },
                        enabled = normalizedGoogleTvHost(host) != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.connect_host)) }
                }
                if (connection == ConnectionState.PAIRING || connection == ConnectionState.CONNECTING) {
                    Text(stringResource(R.string.please_wait))
                }
                if (connection == ConnectionState.CONNECTED) Text(stringResource(R.string.xiaomi_connected))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.retry)) }
                    OutlinedButton(onClick = onForget, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.forget_pairing)) }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
            }
        }
    }
}
