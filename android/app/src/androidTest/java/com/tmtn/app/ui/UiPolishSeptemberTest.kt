package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.common.*
import com.tmtn.app.ui.journal.*
import com.tmtn.app.ui.profile.*
import com.tmtn.app.ui.reference.WaistEstimateUi
import com.tmtn.app.ui.theme.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real production composables, offline fixtures: never create accounts or send records. */
class UiPolishSeptemberTest {
    @get:Rule val compose = createComposeRule()
    @After fun resetAccessibility() { SystemMotionTest.restore() }
    private fun capture(name: String) {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-polish-qa").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun homeShowsWaistAndJournalWithoutDamSummary() {
        val state = CardHomeState(waist = com.tmtn.app.ui.reference.WaistEstimateState { retrofit2.Response.success(emptyList()) }).apply {
            companionLoaded.value = true; companionStage.value = 2
            cardServiceDate.value = "2026-09-15"
        }
        var dam = 0; var journal = 0
        compose.setContent { TMTNv1Theme {
            CardHomeScreen(state, rememberCoroutineScope(), { journal++ }, false, { dam++ })
        } }
        compose.onNodeWithText("오늘의 카드 고르기").assertIsDisplayed()
        capture("01-home")
        compose.onNodeWithText("함께 메우는 내 댐").assertDoesNotExist()
        compose.onNodeWithText("이번 호 펼치기").performScrollTo().performClick()
        compose.onNodeWithText("허리둘레").performScrollTo().assertIsDisplayed()
        capture("02-home-waist-journal")
        compose.runOnIdle { assertEquals(0, dam); assertEquals(1, journal) }
    }
    @Test fun choosingCardKeepsConfirmationAndResetVisible() {
        val state = CardHomeState().apply {
            optionIds.value = listOf("offline-one", "offline-two", "offline-three")
            cardServiceDate.value = "2026-09-15"
        }
        compose.setContent { TMTNv1Theme { DeckPickScreen(state, rememberCoroutineScope(), {}) } }
        compose.onNodeWithText("이 카드로 확정").assertIsNotEnabled()
        compose.onNodeWithContentDescription("2번째 카드").performClick().assertIsSelected()
        compose.onNodeWithText("이 카드로 확정").assertIsDisplayed().assertIsEnabled()
        capture("03-card-selected")
        compose.onNodeWithText("다시 고르기").performClick()
        compose.onNodeWithContentDescription("1번째 카드").performClick().assertIsSelected()
        compose.onNodeWithText("이 카드로 확정").performClick()
        compose.onNodeWithText("이 카드로 확정할까요?").assertIsDisplayed()
        compose.runOnIdle { assertTrue(state.showConfirmDialog.value); assertEquals(0, state.pickedIndex.value) }
    }
    @Composable private fun JournalFixture() {
        val days = (7..13).map { day -> CalendarDayItem("2026-09-${day.toString().padStart(2, '0')}", if (day in listOf(7, 9, 11, 13)) "COMPLETED" else "REST") }
        JournalScreen(JournalLoad.Ready(WeeklyReportResponse("2026-09-07", "2026-09-13", days, 4, 7, "", listOf(WeeklyMaterialItem("WOOD", "나뭇가지", 4)))),
            JournalLoad.Ready(emptyList()), JournalLoad.Failed, peerFixture(75.6), WaistEstimateUi.Unavailable, false, {}, {}, {}, {})
    }
    @Test fun journalShortcutShowsResultsAndPreservesServerRank() {
        compose.setContent { TMTNv1Theme { JournalFixture() } }
        capture("04-journal-cover")
        compose.onNodeWithTag("journal-jump-results").performClick()
        compose.onNodeWithText("75.6").assertIsDisplayed()
        capture("05-journal-results")
        compose.onNodeWithText("또래 100명 중 약 34등").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("25번째쯤").assertDoesNotExist()
    }
    @Test fun journalShortcutAlsoWorksWithReducedMotion() {
        SystemMotionTest.disable()
        compose.setContent { TMTNv1Theme { JournalFixture() } }
        compose.onNodeWithTag("journal-jump-results").performClick()
        compose.onNodeWithText("75.6").assertIsDisplayed()
    }
    @Test fun exerciseChoicesStayUsableAt320DpAndLargeText() {
        var days by mutableIntStateOf(2)
        var intensity by mutableStateOf<String?>("MODERATE")
        compose.setContent { TMTNv1Theme { CompositionLocalProvider(LocalTmtnTextScale provides 1.4f) {
            Column(Modifier.width(320.dp).fillMaxHeight().background(Color.White).verticalScroll(rememberScrollState()).padding(20.dp)) {
                TmtnExerciseFields(days, { days = it }, intensity, { intensity = it }, 60, {}, 90, {}, 0, {})
            }
        } } }
        compose.onNodeWithTag("strength-count-3").performScrollTo().performClick().assertIsSelected()
        capture("06-exercise-large-text")
        compose.onNodeWithTag("strength-intensity-HARD").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(3, days); assertEquals("HARD", intensity) }
        compose.onNodeWithText("유산소 운동").performScrollTo().assertIsDisplayed()
    }
    @Test fun profileKeepsBirthFieldsAndSaveUsable() {
        val state = ProfileState().apply {
            userInfo.value = UserInfoResponse(1, "테스트", "틈튼", "qa@example.invalid", null, 1990, 3, "MALE", false, "2026-09-15")
        }
        compose.setContent { TMTNv1Theme { BasicInfoScreen(state, rememberCoroutineScope(), {}) } }
        compose.onNodeWithText("1990").assertIsDisplayed()
        compose.onNodeWithText("3", substring = false).assertIsDisplayed()
        compose.onNodeWithText("저장", substring = false).assertIsDisplayed().assertIsEnabled()
        capture("07-profile-basic")
    }
    @Test fun damLinksKeepTheirDistinctDestinations() {
        var materials = 0; var collection = 0; var stages = 0
        compose.setContent { TMTNv1Theme {
            com.tmtn.app.ui.dam.DamHomeScreen(CompanionResponse(2, 41, 70, 29,
                listOf(MaterialItem("WOOD", "나뭇가지", "", 12), MaterialItem("METAL", "돌", "", 8),
                    MaterialItem("EARTH", "흙", "", 7), MaterialItem("FIRE", "잎", "", 6), MaterialItem("WATER", "물", "", 8)), emptyList()),
                { materials++ }, { stages++ }, { collection++ })
        } }
        compose.onNodeWithText("모은 재료 살펴보기").performScrollTo().performClick()
        capture("09-dam")
        compose.onNodeWithText("완료한 카드첩").performScrollTo().performClick()
        compose.onNodeWithText("복구 단계 보기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, materials); assertEquals(1, collection); assertEquals(1, stages) }
    }
    @Test fun cardConfirmationStaysVisibleOnShortScreenWithLargeText() {
        val state = CardHomeState().apply { optionIds.value = listOf("one", "two", "three") }
        compose.setContent { TMTNv1Theme { CompositionLocalProvider(LocalTmtnTextScale provides 1.4f) {
            Box(Modifier.width(320.dp).height(640.dp)) { DeckPickScreen(state, rememberCoroutineScope(), {}) }
        } } }
        compose.onNodeWithContentDescription("3번째 카드").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("이 카드로 확정").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("다시 고르기").assertIsDisplayed()
        capture("10-card-short-large-text")
    }
    @Test fun pressCancelsOnDragAndLoadingBlocksDuplicateActions() {
        var count = 0
        var loading by mutableStateOf(false)
        compose.setContent { TMTNv1Theme {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                TmtnActionButton("저장", { count++; loading = true }, TmtnActionStyle.Primary, Modifier.testTag("save"), loading = loading)
            }
        } }
        compose.onNodeWithTag("save").performTouchInput { down(center); moveTo(center.copy(y = center.y + 250f)); up() }
        compose.runOnIdle { assertEquals(0, count) }
        compose.onNodeWithTag("save").performClick().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, count) }
    }
    @Test fun mascotGreetingSettlesAndReducedMotionRemainsStill() {
        compose.setContent { TMTNv1Theme {
            Column(Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
                Text("오늘도 한 틈씩.", style = TmtnType.headline)
                TmtnMascot(com.tmtn.app.R.drawable.beaver_card, "카드를 든 비버", Modifier.size(280.dp), reactToTap = true)
            }
        } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("비버에게 인사하기").performClick()
        compose.mainClock.advanceTimeBy(120)
        capture("08a-beaver-greeting-motion")
        compose.mainClock.advanceTimeBy(1500)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        capture("08-beaver-greeting")
        SystemMotionTest.disable()
        compose.waitForIdle()
        val before = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.onNodeWithContentDescription("비버에게 인사하기").performClick()
        compose.waitForIdle()
        val after = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("Reduced motion must not add or move decorative leaves", before.sameAs(after))
    }
}
