package com.myremote.app.domain

/** Latest authoritative reading plus ordering; equal readings still have distinct revisions. */
data class StreamerPowerObservation(val on: Boolean, val revision: Long)
