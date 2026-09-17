package com.tmtn.app.ui.reference

import com.tmtn.app.ui.cardhome.CardHomeState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PeerScoreReleaseGateTest {
    @Test fun disabledScoreDoesNotContactTheUnconfiguredBridge() = runBlocking {
        // ApiClient를 초기화하지 않아 네트워크에 접근하면 검사가 실패한다.
        val state = ReferenceState(peerScoreEnabled = false)
        state.loadScore()
        assertNull(state.score.value)
        assertNull(state.errorMessage.value)
        assertFalse(state.isLoading.value)
        assertEquals(ReferenceStep.SUMMARY, state.step.value)
        val home = CardHomeState(peerScoreEnabled = false)
        home.tuntunIndexValue.value = 75
        home.tuntunIndexPresentationValue.value = 75.0
        home.loadTuntunIndexSummary()
        assertNull(home.tuntunIndexValue.value)
        assertNull(home.tuntunIndexPresentationValue.value)
        assertFalse(home.tuntunIndexLoadFailed.value)
    }
}
