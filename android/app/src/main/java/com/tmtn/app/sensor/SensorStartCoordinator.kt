package com.tmtn.app.sensor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Only the latest start request in the current login session may launch the service. */
internal class SensorStartCoordinator(private val scope: CoroutineScope, private val sessionKey: () -> String?) {
    private var pending: Job? = null
    private var generation = 0

    fun <T> start(load: suspend () -> T, onReady: (T) -> Unit, onFailure: () -> Unit): Job {
        cancel()
        val request = generation
        val session = sessionKey()
        return scope.launch {
            try {
                if (session == null) { onFailure(); return@launch }
                val value = load()
                ensureActive()
                if (request == generation && sessionKey() == session) onReady(value)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (request == generation && sessionKey() == session) onFailure() }
        }.also { pending = it }
    }

    fun cancel() { generation++; pending?.cancel(); pending = null }
}
