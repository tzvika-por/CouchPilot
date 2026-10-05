package com.myremote.app.google

import com.myremote.app.domain.StreamerPowerObservation
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One bounded latest snapshot, not an event queue. Revision counts every accepted Start frame.
 * Tokens distinguish reconnect attempts even when they share the same connection Job.
 */
internal class GoogleTvPowerObservations {
    class Owner internal constructor(internal val job: Job)
    private var owner: Owner? = null
    private var revision = 0L
    private val mutableState = MutableStateFlow<StreamerPowerObservation?>(null)
    val state = mutableState.asStateFlow()

    @Synchronized fun begin(job: Job): Owner {
        job.ensureActive()
        return Owner(job).also { owner = it }
    }

    @Synchronized fun observe(token: Owner, on: Boolean): Boolean {
        if (owner !== token || !token.job.isActive) return false
        mutableState.value = StreamerPowerObservation(on, ++revision)
        return true
    }

    @Synchronized fun end(token: Owner) {
        if (owner === token) clear()
    }

    @Synchronized fun clear() {
        owner = null
        // Retain the last reading/revision: disconnect is not power telemetry, and
        // must not erase a fresh observation while command completion is pending.
    }
}
