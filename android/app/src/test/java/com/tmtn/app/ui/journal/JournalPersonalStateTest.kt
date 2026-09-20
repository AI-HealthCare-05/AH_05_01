package com.tmtn.app.ui.journal

import com.google.gson.Gson
import com.tmtn.app.network.model.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class JournalPersonalStateTest {
    private fun snapshot(): PersonalXaiSnapshot = Gson().fromJson(
        File("src/debug/assets/journal_review/personal-base.json").readText(), PersonalXaiSnapshot::class.java)

    @Test fun transientConnectionFailureKeepsOnlyTheCurrentVerifiedComparison() = runBlocking {
        val comparison = snapshot().activity_comparison!!
        var calls = 0
        val state = JournalState(personalRequest = {
            if (calls++ == 0) Response.success(PersonalXaiResponse("pending", activity_comparison = comparison))
            else throw IOException("synthetic network outage")
        }, personalPause = {})
        state.refreshPersonal()
        val result = (state.personal as JournalLoad.Ready).value
        assertEquals("unavailable", result.status)
        assertEquals(comparison, result.activity_comparison)
        assertNull(result.snapshot)
    }

    @Test fun authenticationFailureDoesNotKeepThePreviousComparison() = runBlocking {
        var calls = 0
        val state = JournalState(personalRequest = {
            if (calls++ == 0) Response.success(PersonalXaiResponse("pending", activity_comparison = snapshot().activity_comparison))
            else Response.error(401, "".toResponseBody())
        }, personalPause = {})
        state.refreshPersonal()
        assertEquals(JournalLoad.Failed, state.personal)
    }

    @Test fun invalidatedInputCannotBeRestoredByAnOlderInFlightResponse() = runBlocking {
        lateinit var state: JournalState
        state = JournalState(personalRequest = {
            state.invalidatePersonal()
            Response.success(PersonalXaiResponse("ready", snapshot = snapshot()))
        }, personalPause = {})
        state.refreshPersonal()
        assertNull(state.personal)
    }
}
