package com.tmtn.app.sensor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class SensorStartCoordinatorTest {
    @Test fun leavingTheMeasurementCancelsALateStartWithoutAnError() = runBlocking {
        val response = CompletableDeferred<Int>()
        var starts = 0; var errors = 0
        val coordinator = SensorStartCoordinator(this) { "session-a" }
        val job = coordinator.start({ response.await() }, { starts++ }, { errors++ })
        yield()
        coordinator.cancel()
        response.complete(170)
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(0, starts)
        assertEquals(0, errors)
    }

    @Test fun aNewerStartSupersedesThePendingOne() = runBlocking {
        val oldResponse = CompletableDeferred<Int>()
        val starts = mutableListOf<Int>()
        val coordinator = SensorStartCoordinator(this) { "session-a" }
        val old = coordinator.start({ oldResponse.await() }, { starts += it }, { fail("Unexpected failure") })
        yield()
        val current = coordinator.start({ 164 }, { starts += it }, { fail("Unexpected failure") })
        oldResponse.complete(180)
        old.join(); current.join()
        assertEquals(listOf(164), starts)
    }

    @Test fun aChangedLoginCannotStartThePreviousAccountsMeasurement() = runBlocking {
        var session: String? = "session-a"
        var starts = 0
        val response = CompletableDeferred<Int>()
        val coordinator = SensorStartCoordinator(this) { session }
        val job = coordinator.start({ response.await() }, { starts++ }, { fail("Old session must be discarded quietly") })
        yield()
        session = "session-b"
        response.complete(170)
        job.join()
        assertEquals(0, starts)
    }

    @Test fun eachNewStartReadsFreshCalibration() = runBlocking {
        var height = 170; var reads = 0
        val values = mutableListOf<Int>()
        val coordinator = SensorStartCoordinator(this) { "session-a" }
        val load = suspend { reads++; height }
        coordinator.start(load, { values += it }, { fail("Unexpected failure") }).join()
        height = 165
        coordinator.start(load, { values += it }, { fail("Unexpected failure") }).join()
        assertEquals(2, reads)
        assertEquals(listOf(170, 165), values)
    }

    @Test fun startFailuresCanRetryAndSignedOutRequestsNeverLoad() = runBlocking {
        var session: String? = "session-a"
        var errors = 0; var starts = 0
        val coordinator = SensorStartCoordinator(this) { session }
        coordinator.start<Int>({ error("No service") }, { starts++ }, { errors++ }).join()
        coordinator.start({ 170 }, { starts++ }, { errors++ }).join()
        session = null
        coordinator.start({ fail("Must not load when signed out") }, {}, { errors++ }).join()
        assertEquals(1, starts)
        assertEquals(2, errors)
    }
}
