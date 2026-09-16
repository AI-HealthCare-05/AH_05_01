package com.tmtn.app.auth

import com.tmtn.app.network.AuthApi
import com.tmtn.app.network.model.GoogleLoginRequest
import com.tmtn.app.network.model.SocialLoginResponse
import com.tmtn.app.ui.onboarding.OnboardingState
import com.tmtn.app.ui.onboarding.OnboardingStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class GoogleLoginContractTest {
    @Test fun confirmedSignupSendsSelectedVersionedConsentsWithTheAccountRequest() = runBlocking {
        val requests = mutableListOf<GoogleLoginRequest>()
        val state = OnboardingState { api { request ->
            requests += request
            if (!request.signup_confirmed) conflict("SIGNUP_REQUIRED")
            else Response.error(503, """{"detail":"test stops before account creation"}""".toResponseBody())
        } }
        state.exchangeGoogleToken("test-id-token", false) { fail("Signup must wait for consent") }
        state.submitConsents()
        assertEquals(1, requests.size)
        state.agreeTermsOfService.value = true
        state.agreePrivacyPolicy.value = true
        state.agreeAgeOver14.value = true
        state.agreeHealthDataUsage.value = true
        state.agreeHealthDataAnalysis.value = true
        state.submitConsents()
        val request = requests.last()
        assertTrue(request.signup_confirmed)
        assertEquals(setOf("TERMS_OF_SERVICE", "PRIVACY_POLICY", "AGE_OVER_14", "HEALTH_DATA_USAGE", "HEALTH_REFERENCE_ANALYSIS"),
            request.consents.map { it.purpose }.toSet())
        assertTrue(request.consents.all { it.document_version == "v1" })
        assertFalse(state.accountCreated)
    }

    private fun conflict(code: String) = Response.error<SocialLoginResponse>(409,
        """{"code":"$code","email":"member@example.test"}""".toResponseBody())

    @Test fun readsTheExistingServersTwoExplicitConfirmationOutcomes() {
        assertEquals(GoogleLoginOutcome.ConsentRequired("member@example.test"), googleLoginOutcome(conflict("SIGNUP_REQUIRED")))
        assertEquals(GoogleLoginOutcome.LinkRequired("member@example.test"), googleLoginOutcome(conflict("LINK_REQUIRED")))
        assertEquals(GoogleLoginOutcome.SignedIn(SocialLoginResponse("opaque-test-token", false)),
            googleLoginOutcome(Response.success(SocialLoginResponse("opaque-test-token", false))))
    }

    @Test fun emptyTokenAndUnrecognizedConflictNeverCountAsASession() {
        for (response in listOf(Response.success(SocialLoginResponse("", false)), conflict("UNKNOWN"),
            Response.error<SocialLoginResponse>(409, "<html>not json</html>".toResponseBody()))) {
            assertTrue(runCatching { googleLoginOutcome(response) }.isFailure)
        }
        val expired = runCatching { googleLoginOutcome(Response.error(401, "".toResponseBody())) }.exceptionOrNull()
        assertEquals("Google 인증이 만료됐어요. 계정을 다시 선택해 주세요.", expired?.message)
    }

    @Test fun newGoogleAccountWaitsForConsentAndBackReturnsToTheProviderChoice() = runBlocking {
        val requests = mutableListOf<GoogleLoginRequest>()
        val state = OnboardingState { api { request -> requests += request; conflict("SIGNUP_REQUIRED") } }
        var enteredApp = false
        state.exchangeGoogleToken("test-id-token", false) { enteredApp = true }
        assertEquals(OnboardingStep.A06_CONSENT, state.step.value)
        assertTrue(state.isGoogleSignup)
        assertFalse(state.accountCreated)
        assertFalse(enteredApp)
        assertFalse(state.allMandatoryAgreed)
        assertEquals(1, requests.size)
        assertFalse(requests.single().signup_confirmed)
        state.leaveConsent()
        assertEquals(OnboardingStep.AUTH_CHOICE, state.step.value)
        assertFalse(state.isGoogleSignup)
    }

    @Test fun linkingRequiresASecondExplicitRequestAndCancelClearsThePendingChoice() = runBlocking {
        val requests = mutableListOf<GoogleLoginRequest>()
        val state = OnboardingState { api { request -> requests += request; conflict("LINK_REQUIRED") } }
        state.step.value = OnboardingStep.AUTH_CHOICE
        state.exchangeGoogleToken("test-id-token", false) { fail("Must not enter app") }
        assertEquals("member@example.test", state.googleLinkEmail.value)
        assertFalse(requests.single().link_confirmed)
        state.confirmGoogleLink { fail("Must not enter app") }
        assertTrue(requests.last().link_confirmed)
        assertFalse(requests.last().signup_confirmed)
        state.cancelGoogleSignup()
        state.confirmGoogleLink { fail("Cancelled token must not be used") }
        assertEquals(2, requests.size)
        assertNull(state.googleLinkEmail.value)
    }

    @Test fun cancelledExchangeReleasesTheBusyStateWithoutTurningIntoAnError() = runBlocking {
        val state = OnboardingState { api { throw CancellationException() } }
        assertTrue(runCatching { state.exchangeGoogleToken("test", false) {} }.exceptionOrNull() is CancellationException)
        assertFalse(state.isLoading.value)
        assertNull(state.errorMessage.value)
    }

    private fun api(block: (GoogleLoginRequest) -> Response<SocialLoginResponse>): AuthApi =
        java.lang.reflect.Proxy.newProxyInstance(AuthApi::class.java.classLoader, arrayOf(AuthApi::class.java)) { _, method, args ->
            check(method.name == "googleLogin")
            block(args!![0] as GoogleLoginRequest)
        } as AuthApi
}
