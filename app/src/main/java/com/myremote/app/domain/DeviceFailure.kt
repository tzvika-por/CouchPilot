package com.myremote.app.domain

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class FailureKind { NOT_CONNECTED, PERMISSION_DENIED, NETWORK, SECURITY, UNAVAILABLE, UNKNOWN }

open class DeviceFailure(val kind: FailureKind, message: String, cause: Throwable? = null) : IOException(message, cause)

fun failureKind(error: Throwable): FailureKind = when (error) {
    is DeviceFailure -> error.kind
    is SecurityException -> FailureKind.PERMISSION_DENIED
    is SSLException, is java.security.cert.CertificateException -> FailureKind.SECURITY
    is ConnectException, is SocketTimeoutException, is UnknownHostException -> FailureKind.NETWORK
    else -> error.cause?.let(::failureKind) ?: FailureKind.UNKNOWN
}
