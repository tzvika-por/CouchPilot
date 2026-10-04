package com.myremote.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.myremote.app.R
import com.myremote.app.domain.FailureKind

@Composable internal fun failureText(kind: FailureKind?): String = stringResource(when (kind) {
    FailureKind.NOT_CONNECTED -> R.string.error_not_connected
    FailureKind.PERMISSION_DENIED -> R.string.error_permission
    FailureKind.NETWORK -> R.string.error_network
    FailureKind.SECURITY -> R.string.error_security
    FailureKind.UNAVAILABLE -> R.string.error_unavailable
    else -> R.string.error_generic
})
