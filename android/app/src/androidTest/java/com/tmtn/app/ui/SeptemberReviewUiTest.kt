package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.journal.*
import com.tmtn.app.ui.legal.*
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.profile.*
import com.tmtn.app.ui.reference.WaistEstimateUi
import com.tmtn.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Isolated, offline screen fixtures. No signup, consent, logout or profile API calls. */
class SeptemberReviewUiTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "september-review").apply { mkdirs() }
        compose.onNodeWithTag("review-stage").captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun eachSignupDocumentOpensItsOwnContentWithoutChangingConsent() {
        val state = OnboardingState()
        compose.setContent { TMTNv1Theme { Box(Modifier.fillMaxSize().testTag("review-stage")) {
            if (state.step.value == OnboardingStep.A14_TERMS_DETAIL) A14TermsDetailScreen(state)
            else A06ConsentScreen(state, rememberCoroutineScope())
        } } }
        capture("consent-top")
        val labels = listOf(
            "[필수] 서비스 이용약관" to LegalDocument.TERMS,
            "[필수] 개인정보 수집 · 이용 동의" to LegalDocument.PERSONAL_DATA,
            "[필수] 건강정보 수집 · 이용 동의" to LegalDocument.HEALTH_DATA,
            "[선택] 위치정보 수집 · 이용 동의" to LegalDocument.LOCATION,
            "[선택] 틈튼지수 산출을 위한 분석" to LegalDocument.ANALYSIS,
            "[선택] 카드 · 미션 알림 받기" to LegalDocument.NOTIFICATIONS)
        for ((label, document) in labels) {
            compose.onNodeWithContentDescription("$label 보기").performScrollTo().performClick()
            compose.onNodeWithTag("legal-${document.name}").assertIsDisplayed()
            compose.onNodeWithText(document.sections().first().title).assertIsDisplayed()
            if (document == LegalDocument.NOTIFICATIONS) capture("notification-consent")
            compose.onNodeWithText("닫기").performScrollTo().performClick()
            compose.runOnIdle { assertFalse(state.allMandatoryAgreed); assertFalse(state.agreeMarketingPush.value) }
        }
        compose.onNodeWithText("개인정보 처리방침 읽기").performScrollTo().performClick()
        compose.onNodeWithTag("legal-PRIVACY_NOTICE").assertIsDisplayed()
        capture("privacy-notice")
        compose.onNodeWithText("닫기").performScrollTo().performClick()
        compose.runOnIdle {
            state.agreeTermsOfService.value = true; state.agreePrivacyPolicy.value = true
            state.agreeAgeOver14.value = true; state.agreeHealthDataUsage.value = true
        }
        compose.onNodeWithText("동의하고 가입 완료").performScrollTo().assertIsEnabled()
        compose.runOnIdle { assertFalse(state.agreeMarketingPush.value); assertFalse(state.agreeLocationUsage.value) }
    }

    @Test fun profilePrivacyAndTermsHaveDifferentRoutesAndReturnToTheirOrigin() {
        val state = ProfileState().apply { screen.value = ProfileScreenKey.APP_INFO }
        compose.setContent { TMTNv1Theme {
            if (state.screen.value == ProfileScreenKey.TERMS) TermsDetailScreen(state.legalDocument.value) { state.screen.value = state.termsOrigin }
            else AppInfoScreen({}, state::openLegal)
        } }
        compose.onNodeWithText("개인정보 처리방침").performClick()
        compose.onNodeWithTag("legal-PRIVACY_NOTICE").assertIsDisplayed()
        compose.onNodeWithText("닫기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ProfileScreenKey.APP_INFO, state.screen.value) }
        compose.onNodeWithText("서비스 이용약관").performClick()
        compose.onNodeWithTag("legal-TERMS").assertIsDisplayed()
    }

    @Test fun consentDocumentsRemainReadableAtLargeTypeOnCompactScreen() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                TMTNv1Theme { Box(Modifier.width(320.dp).fillMaxHeight().testTag("review-stage")) {
                    TermsDetailScreen(LegalDocument.NOTIFICATIONS) {}
                } }
            }
        }
        compose.onNodeWithText("기기 알림 권한은 별도예요").performScrollTo().assertIsDisplayed()
        capture("notification-large-320")
        compose.onNodeWithText("닫기").performScrollTo().assertIsDisplayed()
    }

    @Test fun weeklyEditionStartsOnMondayAndFutureDaysCannotBeSelected() {
        val raw = WeeklyReportResponse("2026-09-10", "2026-09-16", (10..16).map {
            CalendarDayItem("2026-09-$it", if (it == 16 || it == 10) "COMPLETED" else "INCOMPLETE")
        }, 2, 7, null, emptyList())
        val weekly = mondayEdition(raw)!!
        val cards = listOf(CardHistoryItem("내일 기대 적기", "WOOD", "나뭇가지", "", "2026-09-16"))
        compose.setContent { TMTNv1Theme { Box(Modifier.fillMaxSize().testTag("review-stage")) {
            JournalScreen(JournalLoad.Ready(weekly.copy(materials_this_week = weekMaterials(cards))), JournalLoad.Ready(cards),
                JournalLoad.Failed, null, WaistEstimateUi.Loading, false, {}, {}, {}, {})
        } } }
        compose.onNodeWithText("1일의 실천, 이번 주에 남았어요.").performScrollTo().assertIsDisplayed()
        capture("weekly-monday-top")
        compose.onNodeWithTag("journal-week-dates").performScrollTo()
        compose.onNodeWithContentDescription("9월 14일, 미완료").assertIsDisplayed()
        compose.onNodeWithContentDescription("9월 20일, 아직 오지 않은 날").assertIsNotEnabled()
        compose.onNodeWithContentDescription("9월 10일, 실천").assertDoesNotExist()
        compose.onNodeWithContentDescription("9월 16일, 실천").performClick().assertIsSelected()
        capture("weekly-monday-dates")
    }
}
