package com.tmtn.app.ui.onboarding

/** Resumable client request order using the existing API contract. */
internal class RegistrationProgress(accountAlreadyCreated: Boolean = false) {
    var accountCreated: Boolean = accountAlreadyCreated
        private set
    private val savedPurposes = mutableSetOf<String>()

    suspend fun complete(
        requiredAgreed: Boolean,
        purposes: List<String>,
        createAccount: suspend () -> Unit,
        saveConsent: suspend (String) -> Unit,
    ) {
        check(requiredAgreed) { "필수 약관을 확인해 주세요." }
        if (!accountCreated) {
            createAccount()
            accountCreated = true
        }
        for (purpose in purposes) {
            if (purpose !in savedPurposes) {
                saveConsent(purpose)
                savedPurposes += purpose
            }
        }
    }
}
