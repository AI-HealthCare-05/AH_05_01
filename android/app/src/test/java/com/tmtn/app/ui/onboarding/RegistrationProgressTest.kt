package com.tmtn.app.ui.onboarding

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RegistrationProgressTest {
    @Test fun noAccountOrConsentRequestsBeforeRequiredAgreement() = runBlocking {
        val calls = mutableListOf<String>()
        val result = runCatching {
            RegistrationProgress().complete(false, listOf("TERMS"), { calls += "create" }, { calls += it })
        }
        assertTrue(result.isFailure)
        assertTrue(calls.isEmpty())
    }

    @Test fun verificationFailureDoesNotSubmitAnyConsent() = runBlocking {
        val progress = RegistrationProgress()
        val calls = mutableListOf<String>()
        val result = runCatching {
            progress.complete(true, listOf("TERMS"), { error("invalid code") }, { calls += it })
        }
        assertTrue(result.isFailure)
        assertFalse(progress.accountCreated)
        assertTrue(calls.isEmpty())
    }

    @Test fun consentRetryDoesNotCreateAnotherAccountOrRepeatSavedPurposes() = runBlocking {
        val progress = RegistrationProgress()
        val calls = mutableListOf<String>()
        var failPrivacy = true
        suspend fun submit() = progress.complete(true, listOf("TERMS", "PRIVACY"),
            { calls += "create" }, { purpose ->
                calls += purpose
                if (purpose == "PRIVACY" && failPrivacy) error("connection lost")
            })
        assertTrue(runCatching { submit() }.isFailure)
        assertTrue(progress.accountCreated)
        failPrivacy = false
        submit()
        assertEquals(listOf("create", "TERMS", "PRIVACY", "PRIVACY"), calls)
    }

    @Test fun resumedAccountNeverReusesAnExpiredVerificationCode() = runBlocking {
        val calls = mutableListOf<String>()
        RegistrationProgress(accountAlreadyCreated = true).complete(true, listOf("TERMS"),
            { error("must not verify again") }, { calls += it })
        assertEquals(listOf("TERMS"), calls)
    }
}
