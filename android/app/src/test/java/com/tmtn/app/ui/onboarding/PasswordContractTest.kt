package com.tmtn.app.ui.onboarding

import org.junit.Assert.*
import org.junit.Test

class PasswordContractTest {
    @Test fun signupAndAccountEditorFollowTheExistingServerAsciiRules() {
        assertTrue(isValidPassword("Sample29!"))
        assertFalse(isValidPassword("sample29!"))
        assertFalse(isValidPassword("SAMPLE29!"))
        assertFalse(isValidPassword("SampleABC!"))
        assertFalse(isValidPassword("Sample292"))
        assertFalse(isValidPassword("Σample29!"))
        assertFalse(isValidPassword("Samplｅ29!".uppercase()))
    }
}
