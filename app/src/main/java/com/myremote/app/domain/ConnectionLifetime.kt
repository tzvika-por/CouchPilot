package com.myremote.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread service lease. Activity stop/recreation does not release it. */
class ConnectionLifetime(private val open: () -> Unit, private val close: () -> Unit) {
    private val mutableActive = MutableStateFlow(false)
    val active = mutableActive.asStateFlow()

    fun start() {
        if (mutableActive.value) return
        try {
            open()
            mutableActive.value = true
        } catch (error: Exception) {
            // A partial start still owns resources that must be released.
            close()
            throw error
        }
    }

    fun stop() {
        if (!mutableActive.value) return
        mutableActive.value = false
        close()
    }
}
