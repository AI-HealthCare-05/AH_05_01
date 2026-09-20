package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.tmtn.app.network.CardHomeApi
import com.tmtn.app.network.ProfileApi
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.journal.*
import com.tmtn.app.ui.nav.*
import com.tmtn.app.ui.onboarding.AuthChoiceScreen
import com.tmtn.app.ui.profile.*
import com.tmtn.app.ui.reference.*
import com.tmtn.app.ui.theme.*
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/** Production screens on device with isolated fixture data; no live account or measurement writes. */
class SeptemberQaFixesTest {
    @get:Rule val compose = createComposeRule()
    private val card = CardRevealResponse("qa-card", "SENSOR_RUNNING_DISTANCE", "빠른 속도로 달리기", "편안한 장소에서 달려요.",
        "WOOD", "유산소", 200, "m", "READY", fortune_text = "밝은 방향으로 향하면 마음도 한결 가벼워져요.", lucky_location = "집 주변", line_text = "오늘 가능한 만큼 시작해요.")
    private val window = CardWindowResponse("SELECTED", "2026-09-15", "qa-set", emptyList(), "one", "qa-card", "READY")
    private val report = WeeklyReportResponse("2026-09-09", "2026-09-15", (9..15).map {
        CalendarDayItem("2026-09-$it", if (it == 15) "COMPLETED" else "REST") }, 1, 7, null, emptyList())
    private fun homeState() = CardHomeState(waist = WaistEstimateState { retrofit2.Response.success(emptyList()) })
    @Composable private fun Page(tab: MainTab? = null, content: @Composable () -> Unit) {
        TMTNv1Theme {
            Column(Modifier.fillMaxSize().background(LocalTmtnColors.current.background)) {
                Box(Modifier.weight(1f)) { content() }
                if (tab != null) BottomNavBar(tab, {})
            }
        }
    }
    private fun capture(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "qa-fixes").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Composable private fun Journal(onGo: () -> Unit = {}) {
        JournalScreen(JournalLoad.Ready(report), JournalLoad.Ready(emptyList()), JournalLoad.Ready(JournalToday(window, card)),
            peerFixture(61.2), WaistEstimateUi.Unavailable, true, {}, onGo, {}, {})
    }
    @Test fun selectedHomeHasReadableCardAndNoDamSummary() {
        val state = homeState().apply {
            cardServiceDate.value = "2026-09-15"; drawState.value = "SELECTED"
            todayChallengeState.value = "READY"; todayChallengeId.value = card.challenge_id; revealedCard.value = card
        }
        compose.setContent { Page(MainTab.HOME) { CardHomeScreen(state, rememberCoroutineScope(), showDebugTools = false) } }
        compose.onNodeWithText("이 카드 실천하기").assertIsDisplayed()
        compose.onNodeWithText("함께 메우는 내 댐").assertDoesNotExist()
        capture("01-home-selected")
        compose.onNodeWithText("허리둘레").performScrollTo().assertIsDisplayed()
        capture("02-home-waist-location")
    }
    @Test fun independentWaistCardShowsEstimateAndExpandsExplanation() {
        var expanded by mutableStateOf(false)
        compose.setContent { Page(MainTab.HOME) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                HomeWaistCard(WaistEstimateUi.Available(BigDecimal("79.2"),null),expanded,{expanded=!expanded},{})
            }
        } }
        compose.onNodeWithText("79.2 cm").assertIsDisplayed()
        capture("03-waist-summary")
        compose.onNodeWithText("허리둘레 자세히 보기").performClick()
        compose.onNodeWithContentDescription("모델이 추정한 허리둘레 약 79.2 센티미터").assertIsDisplayed()
        compose.onNodeWithText("cm · 추정값").assertDoesNotExist()
        compose.onNodeWithText("줄자로 잰 값과는 달라요").performScrollTo().assertIsDisplayed()
        capture("04-waist-expanded")
        compose.onNodeWithText("설명 접기").performScrollTo().performClick()
        compose.onNodeWithText("줄자로 잰 값과는 달라요").assertDoesNotExist()
    }
    @Test fun journalArtworkAndFooterHaveClearNavigation() {
        compose.setContent { Page(MainTab.REFERENCE) { Journal() } }
        compose.onNodeWithText("새로고침").assertDoesNotExist()
        compose.onNodeWithText("불러오는 중").assertDoesNotExist()
        capture("05-newspaper-cover")
        compose.onNodeWithContentDescription("함께 산책을 권하는 틈튼이").performScrollTo().assertIsDisplayed()
        capture("06-newspaper-character")
        compose.onNodeWithTag("journal-card-entry").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("신문을 덮으며").assertDoesNotExist()
        capture("07-newspaper-card-entry")
        compose.onNodeWithText("내 신체·운동 정보").performScrollTo()
        capture("08-newspaper-results")
    }
    @Test fun journalButtonOpensRealCardFlowWithoutStartingMeasurement() {
        val calls = mutableListOf<String>()
        val api = java.lang.reflect.Proxy.newProxyInstance(CardHomeApi::class.java.classLoader, arrayOf(CardHomeApi::class.java)) { _, method, _ ->
            calls.add(method.name)
            when (method.name) {
                "getTodayCards" -> retrofit2.Response.success(window)
                "revealChallenge" -> retrofit2.Response.success(card)
                else -> error("Unexpected mutation/request: ${method.name}")
            }
        } as CardHomeApi
        val state = CardHomeState(missionApiProvider = { api })
        var tab by mutableStateOf(MainTab.REFERENCE)
        compose.setContent { Page(if (tab == MainTab.REFERENCE) tab else null) {
            if (tab == MainTab.REFERENCE) Journal {
                state.cardEntryRequested.value = true
                tab = MainTab.HOME
            } else CardHomeFlow(state, { true }, onStartSensorTracking = { _,_,_,_ -> error("Must not start sensor") },
                onStopSensorTracking = {}, onOpenSettings = {})
        } }
        compose.onNodeWithTag("journal-card-entry").performScrollTo()
        compose.onNodeWithText("오늘의 카드로 가기").performClick()
        compose.waitUntil(10_000) { state.step.value == CardHomeStep.REVEALED }
        compose.onNodeWithText("오늘의 카드").assertIsDisplayed()
        compose.onNodeWithText("이 행동 시작하기").assertIsDisplayed()
        capture("09-journal-to-card")
        compose.runOnIdle { assertTrue(calls.isNotEmpty()); assertTrue(calls.all { it in listOf("getTodayCards","revealChallenge") }) }
    }
    @Test fun cardMessageFitsTwoLinesAtNormalSizeAndGrowsWithoutClipping() {
        compose.setContent { Page {
            Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp)) {
                TmtnMissionCard(card, LocalDate.of(2026,9,15))
            }
        } }
        var lines = 0
        compose.onNodeWithText(card.fortune_text!!).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>(); it(results)
            lines = results.single().lineCount
            assertFalse(results.single().hasVisualOverflow)
        }
        assertTrue("Fortune should fit two lines on a 360dp screen: $lines",lines <= 2)
        capture("10-card-typography")
    }
    @Test fun genderCannotBeEditedAndHealthSaveDoesNotPatchIt() {
        val calls = mutableListOf<String>()
        val health = HealthInputResponse("health","2026-09-15",mapOf("height_cm" to 172,"weight_kg" to 73),mapOf("height_cm" to "cm","weight_kg" to "kg"),"MANUAL","2026-09-15")
        val api = java.lang.reflect.Proxy.newProxyInstance(ProfileApi::class.java.classLoader,arrayOf(ProfileApi::class.java)) { _, method, args ->
            calls.add(method.name)
            check(method.name == "submitHealthInput") { "Gender must never be patched: ${method.name}" }
            assertEquals(mapOf("height_cm" to 172,"weight_kg" to 73),(args!![0] as HealthInputRequest).input_values)
            retrofit2.Response.success(health)
        } as ProfileApi
        val state = ProfileState { api }.apply {
            userInfo.value = UserInfoResponse(1,"테스트","틈튼","qa@example.invalid",null,1993,7,"MALE",false,"2026-09-15")
            healthInput.value = health
        }
        compose.setContent { Page(MainTab.MY) { HealthEditScreen(state, rememberCoroutineScope(), {}) } }
        compose.onNodeWithText("남성").assertHasNoClickAction()
        compose.onNodeWithText("여성").assertDoesNotExist()
        compose.onNodeWithText("몸 정보").assertDoesNotExist()
        capture("11-health-read-only-gender")
        compose.onNodeWithText("신체 정보 저장").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("submitHealthInput"),calls); assertEquals("MALE",state.userInfo.value?.gender) }
    }
    @Test fun permissionsHaveComfortableInsetAndAuthCaptionIsCentered() {
        var auth by mutableStateOf(false)
        compose.setContent { Page(if (auth) null else MainTab.MY) {
            if (!auth) PermissionsScreen({}, {auth=true})
            else AuthChoiceScreen({}, {}, {}, {}, googleConfigured=true)
        } }
        val text = compose.onNodeWithText("신체 활동").getUnclippedBoundsInRoot()
        capture("12-permissions-padding")
        // Each 20dp padding rounds separately at the device's density.
        with(compose.density) {
            assertEquals("Screen 20dp + card 20dp inset; actual=${text.left}",
                (20.dp.roundToPx() * 2).toFloat(), text.left.toPx(), 0.51f)
        }
        compose.onNodeWithText("기기 설정 열기").performScrollTo().performClick()
        compose.onNodeWithText("작은 실천, 틈튼이와 함께해요.").performScrollTo().assertIsDisplayed()
        capture("13-auth-caption")
    }
    @Test fun distanceScreenUsesMetersWithUnchangedProgress() {
        val state = homeState().apply { revealedCard.value = card.copy(state="ACTIVE") }
        com.tmtn.app.sensor.SensorDataHolder.resetAll()
        compose.setContent { Page { SensorMeasuringScreen(state, rememberCoroutineScope(), {}, {}, {}) } }
        compose.onNodeWithText("0 m").assertIsDisplayed()
        compose.onNodeWithText("목표 200 m").assertIsDisplayed()
        compose.onNodeWithText("0% 달성").assertIsDisplayed()
        capture("14-distance-meters")
    }
}
