package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.onboarding.WelcomeStory
import com.tmtn.app.ui.profile.SoundSettingsScreen
import com.tmtn.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class AppFlowPolishTest {
    @get:Rule val compose = createComposeRule()
    private val card = CardRevealResponse("offline-fixture", "SENSOR_WALKING_DURATION", "익숙한 길에서 가볍게 걷기", "움직이는 동안만 시간을 기록해요.",
        "WOOD", "유산소", 10, "분", "READY", fortune_text = "서두르지 않아도,\n오늘의 한 걸음은 남아요.", lucky_location = "가까운 산책길", line_text = "편안한 속도로 10분 걸어보기")

    @Composable private fun Stage(content: @Composable () -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val resolver = remember(context) {
            val config = android.content.res.Configuration(context.resources.configuration).apply { fontWeightAdjustment = 0 }
            androidx.compose.ui.text.font.createFontFamilyResolver(context.createConfigurationContext(config))
        }
        CompositionLocalProvider(androidx.compose.ui.platform.LocalFontFamilyResolver provides resolver) {
            TMTNv1Theme { Box(Modifier.fillMaxSize().background(LocalTmtnColors.current.background).testTag("flow-stage")) { content() } }
        }
    }

    @Test fun welcomeCanBeSkippedAndNeverAutoAdvances() {
        var signups = 0
        compose.setContent { Stage { WelcomeStory({ signups++ }, {}) } }
        compose.mainClock.advanceTimeBy(5000)
        compose.onNodeWithText("틈튼이 만나기").assertIsDisplayed().performClick()
        compose.onNodeWithText("안녕,\n나는 틈튼이야.").assertIsDisplayed()
        capture("welcome-1")
        compose.onNodeWithText("바로 시작할게").performClick()
        compose.runOnIdle { assertEquals(1, signups) }
    }

    @Test fun welcomeStoriesLeadToTheExistingSignup() {
        var signups = 0
        compose.setContent { Stage { WelcomeStory({ signups++ }, {}) } }
        compose.onNodeWithText("틈튼이 만나기").performClick()
        compose.onNodeWithText("어떤 댐인데?").performClick()
        compose.onNodeWithText("바쁜 하루 사이에\n작은 틈이 생겼더라.").assertIsDisplayed()
        capture("welcome-2")
        compose.onNodeWithText("어떻게 메우면 될까?").performClick()
        capture("welcome-3")
        compose.onNodeWithText("내 댐도 만나볼래").performClick()
        compose.runOnIdle { assertEquals(1, signups) }
    }

    @Test fun selectedMissionAppearsOnHomeAndKeepsItsActualTitle() {
        val state = CardHomeState().apply {
            drawState.value = "SELECTED"; todayChallengeId.value = card.challenge_id
            todayChallengeState.value = "READY"; revealedCard.value = card
            companionLoaded.value = true; companionStage.value = 2
        }
        compose.setContent { Stage { CardHomeScreen(state, rememberCoroutineScope(), showDebugTools = false) } }
        compose.onNodeWithText(card.title).assertIsDisplayed()
        compose.onNodeWithText("틈튼 움직임 인식").assertIsDisplayed()
        capture("home-selected")
    }

    @Test fun cardBackSelectionAndDialogLoadingAreAccessible() {
        val state = CardHomeState().apply { optionIds.value = listOf("a", "b", "c") }
        compose.setContent { Stage { DeckPickScreen(state, rememberCoroutineScope(), {}) } }
        compose.onNodeWithContentDescription("2번째 카드").performClick().assertIsSelected()
        capture("card-back")
        compose.onNodeWithContentDescription("1번째 카드").assertIsNotEnabled()
        compose.onNodeWithText("이 카드로 확정").performClick()
        compose.runOnIdle { state.isLoading.value = true }
        compose.onNodeWithText("카드 여는 중…").assertIsNotEnabled()
    }

    @Test fun cardFrontAndSoundControlsRenderWithoutBackendCalls() {
        var sound by mutableStateOf(false)
        compose.setContent { Stage {
            if (sound) SoundSettingsScreen({}) else RevealScreen(CardHomeState().apply { revealedCard.value = card }, rememberCoroutineScope(), {})
        } }
        compose.onNodeWithText("오늘의 목표").performScrollTo().assertIsDisplayed()
        capture("card-front")
        compose.runOnIdle { sound = true }
        compose.onNodeWithText("소리도 편안한 만큼").assertIsDisplayed()
        capture("sound-settings")
    }

    @Test fun damUsesRepairStagesAndKeepsItsCollectionReachable() {
        var opened = false
        val companion = com.tmtn.app.network.model.CompanionResponse(3, 42, 70, 28,
            listOf(com.tmtn.app.network.model.MaterialItem("WOOD", "나뭇가지", "유산소", 42)), emptyList())
        compose.setContent { Stage {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    com.tmtn.app.ui.dam.DamHomeScreen(companion, {}, {}, { opened = true })
                }
                com.tmtn.app.ui.nav.BottomNavBar(com.tmtn.app.ui.nav.MainTab.DAM, {})
            }
        } }
        compose.onNodeWithText("3단계 · 몸통 연결하기").assertIsDisplayed()
        capture("dam-3")
        compose.onNodeWithText("완료한 카드첩").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun enlargedWelcomeKeepsTheNextActionVisible() {
        compose.setContent { Stage {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.8f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { WelcomeStory({}, {}) }
            }
        } }
        compose.onNodeWithText("틈튼이 만나기").assertIsDisplayed().performClick()
        compose.onNodeWithText("어떤 댐인데?").assertIsDisplayed().performClick()
        compose.onNodeWithText("어떻게 메우면 될까?").assertIsDisplayed()
        compose.onNodeWithText("하루를 전부 바꿀 필요는 없어.\n오늘 손볼 수 있는 한 곳부터 같이 보자.").performScrollTo().assertIsDisplayed()
        capture("welcome-large-320")
    }

    @Test fun sensorControlsStayReachableWithLargeText() {
        val state = CardHomeState().apply { revealedCard.value = card }
        compose.setContent { Stage {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.8f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    SensorMeasuringScreen(state, rememberCoroutineScope(), {}, {}, {})
                }
            }
        } }
        compose.onNodeWithText("일시정지").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("완료하기").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        capture("sensor-large-320")
    }

    @Test fun profileRequiresBirthMonthAndAnExplicitPregnancyAnswer() {
        val state = com.tmtn.app.ui.onboarding.OnboardingState().apply {
            name.value = "지우"; heightCm.value = "170"; weightKg.value = "64"; gender.value = "FEMALE"
        }
        compose.setContent { Stage { com.tmtn.app.ui.onboarding.A07ProfileScreen(state, rememberCoroutineScope()) } }
        compose.onNodeWithText("평소 운동 알려주기").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("태어난 연도").performScrollTo().performTextInput("1991")
        compose.onNodeWithText("월").performScrollTo().performTextInput("9")
        compose.onNodeWithText("평소 운동 알려주기").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("아니요").performScrollTo().performClick()
        compose.onNodeWithText("평소 운동 알려주기").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("너의 하루를 알려 줘.").performScrollTo()
        capture("profile-form")
    }

    @Test fun exerciseRequiresIntensityOnlyWhenStrengthIsSelected() {
        val state = com.tmtn.app.ui.onboarding.OnboardingState()
        compose.setContent { Stage { com.tmtn.app.ui.onboarding.A08ExerciseScreen(state, rememberCoroutineScope(), { true }) } }
        compose.onNodeWithText("다음으로").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("strength-count-2").performScrollTo().performClick()
        compose.onNodeWithText("다음으로").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("strength-intensity-MODERATE").performScrollTo().performClick()
        compose.onNodeWithText("다음으로").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { assertEquals(0, state.aerobicModerateMinutes.value) }
        compose.onNodeWithText("고강도").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("다음으로").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, -10000f) }
        compose.onNodeWithText("평소 운동량을\n알려 주세요.").assertIsDisplayed()
        capture("exercise-form")
    }

    @Test fun exerciseFooterStaysVisibleAtLargeTextAcrossEveryIntensity() {
        val previousLargeControls = AccessibilitySettingsHolder.largeControlsEnabled.value
        AccessibilitySettingsHolder.largeControlsEnabled.value = true
        try {
            val state = com.tmtn.app.ui.onboarding.OnboardingState().apply { strengthWeeklyCount.value = 2 }
            var densityScale = 1f
            compose.setContent {
                val density = androidx.compose.ui.platform.LocalDensity.current
                densityScale = density.density
                CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.8f)) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) { Stage { com.tmtn.app.ui.onboarding.A08ExerciseScreen(state, rememberCoroutineScope(), { true }) } }
                }
            }
            compose.onNodeWithText("다음으로").assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithTag("strength-count-2").performScrollTo().assertIsDisplayed()
            assertTrue(compose.onNodeWithTag("strength-count-2").fetchSemanticsNode().boundsInRoot.height >= 60f * densityScale - 1f)
            compose.onNodeWithTag("strength-intensity-MODERATE").performScrollTo().performClick()
            for (label in listOf("저강도", "중강도", "고강도")) {
                compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("다음으로").assertIsDisplayed().assertIsEnabled()
            }
            capture("exercise-large-320")
        } finally { AccessibilitySettingsHolder.largeControlsEnabled.value = previousLargeControls }
    }

    @Test fun collectionFiltersHaveARecoveryAndCardsKeepTheirRealTitles() {
        val item = com.tmtn.app.network.model.CardHistoryItem("점심 뒤 10분 걷기", "WOOD", "나뭇가지", "유산소", "2026-09-14T03:00:00Z")
        var filter by mutableStateOf<String?>(null)
        var opened: String? = null
        compose.setContent { Stage {
            com.tmtn.app.ui.dam.CollectionContent(
                com.tmtn.app.network.model.CardCollectionResponse(if (filter == null) 1 else 0, if (filter == null) listOf(item) else emptyList()),
                false, filter, { filter = it }, {}, { opened = it.title }, {}, {})
        } }
        compose.onNodeWithText("점심 뒤 10분 걷기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(item.title, opened) }
        compose.onNodeWithText("새잎").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("이 재료의 카드는 아직 없어요.").assertIsDisplayed()
        compose.onNodeWithText("전체 카드 보기").performClick()
        compose.onNodeWithText(item.title).performScrollTo().assertIsDisplayed()
        capture("card-collection")
    }

    @Test fun profileHomeKeepsDamBasicInfoAndStoryReachable() {
        val state = com.tmtn.app.ui.profile.ProfileState().apply {
            userInfo.value = com.tmtn.app.network.model.UserInfoResponse(1L, "지우", "지우", "jiwoo@example.com", null, 1991, 9, "FEMALE", false, "2026-09-01T00:00:00Z")
            companionMaterials.value = 42; companionStage.value = 3
        }
        var destination: com.tmtn.app.ui.profile.ProfileScreenKey? = null
        var openedDam = false
        compose.setContent { Stage { com.tmtn.app.ui.profile.ProfileHomeScreen(state, { destination = it }, { openedDam = true }) } }
        capture("profile-home")
        compose.onNodeWithText("모은 재료 42개 · 댐 3단계").performClick()
        compose.runOnIdle { assertTrue(openedDam) }
        compose.onNodeWithText("기본 정보").performClick()
        compose.runOnIdle { assertEquals(com.tmtn.app.ui.profile.ProfileScreenKey.BASIC, destination) }
        compose.onNodeWithText("틈튼이 이야기 다시 보기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(com.tmtn.app.ui.profile.ProfileScreenKey.STORY, destination) }
    }

    @Test fun sevenDaysShowRealComparisonAndKeepRestDays() {
        val state = com.tmtn.app.ui.record.RecordState().apply {
            tab.value = com.tmtn.app.ui.record.RecordTab.WEEKLY
            weeklyReport.value = com.tmtn.app.network.model.WeeklyReportResponse("2026-09-08", "2026-09-14",
                (8..14).map { com.tmtn.app.network.model.CalendarDayItem("2026-09-${it.toString().padStart(2, '0')}", if (it in listOf(8, 10, 11, 13)) "COMPLETED" else if (it == 9) "REST" else "INCOMPLETE") },
                4, 7, null, listOf(com.tmtn.app.network.model.WeeklyMaterialItem("WOOD", "나뭇가지", 4)))
            previousWeek.value = (1..7).map { com.tmtn.app.network.model.CalendarDayItem("2026-09-0$it", if (it <= 3) "COMPLETED" else "INCOMPLETE") }
        }
        var opened = false
        compose.setContent { Stage { com.tmtn.app.ui.record.WeeklyReportScreen(state, rememberCoroutineScope(), {}, { opened = true }) } }
        compose.onNodeWithText("실천 4일 · 쉼 1일").assertIsDisplayed()
        compose.onNodeWithText("지난번보다 1일 더 실천했어요.").assertIsDisplayed()
        capture("record-seven-days")
        compose.onNodeWithText("주간면 읽기").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun monthlyCalendarPreservesCompletedRestAndIncompleteDates() {
        val state = com.tmtn.app.ui.record.RecordState().apply {
            year.value = 2026; month.value = 9
            streak.value = com.tmtn.app.network.model.StreakResponse(2, 6, 1, 1)
            monthlyCalendar.value = com.tmtn.app.network.model.MonthlyCalendarResponse(2026, 9,
                (1..14).map { com.tmtn.app.network.model.CalendarDayItem("2026-09-${it.toString().padStart(2, '0')}",
                    if (it in listOf(3, 10)) "REST" else if (it in listOf(7, 12)) "INCOMPLETE" else "COMPLETED") }, 10, 2)
        }
        var selected: String? = null
        compose.setContent { Stage {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { com.tmtn.app.ui.record.MonthlyCalendarScreen(state, rememberCoroutineScope(), {}, onDateSelected = { selected = it }) }
                com.tmtn.app.ui.nav.BottomNavBar(com.tmtn.app.ui.nav.MainTab.RECORD, {})
            }
        } }
        compose.onNodeWithText("2026년 9월").assertIsDisplayed()
        compose.onNodeWithText("연속 기록").assertExists()
        compose.onNodeWithText("가장 오래 이어간 기록 6일").assertExists()
        capture("record-month")
        compose.onNodeWithContentDescription("10일, 쉼").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("2026-09-10", selected) }
    }

    @Test fun memoFailurePreservesTheDraftAndDoesNotCoverItsAction() {
        val state = com.tmtn.app.ui.record.RecordState().apply {
            selectedDate.value = "2026-09-14"; showDaySheet.value = true
            dayDetail.value = com.tmtn.app.network.model.DayDetailResponse("2026-09-14", "COMPLETED", "점심 뒤 10분 걷기", "SENSOR_WALKING_DURATION", "2026-09-14T03:41:00Z", 600, null, "WOOD", "나뭇가지", null)
        }
        compose.setContent { Stage { com.tmtn.app.ui.record.DayDetailSheet(state, rememberCoroutineScope()) } }
        capture("record-day")
        compose.onNodeWithText("메모 남기기").performScrollTo().performClick()
        compose.onNodeWithText("한 줄 메모").performScrollTo().performTextInput("바람이 시원해서 걷기 좋았다.")
        // IME resizing runs on the platform clock, after Compose text input becomes idle.
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(300, 3000)
        compose.runOnIdle { state.memoError.value = "메모를 저장하지 못했어요. 입력한 내용은 그대로예요." }
        compose.onNodeWithText("바람이 시원해서 걷기 좋았다.").assertExists()
        compose.onNodeWithText("저장").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("memo-error")
    }

    @Test fun walkingOnlyRequestsActivityAndContinuesAfterPermission() {
        var granted by mutableStateOf(false)
        var requested: String? = null
        var starts = 0
        val state = CardHomeState().apply { revealedCard.value = card.copy(state = "ACTIVE"); step.value = CardHomeStep.SENSOR_INTRO }
        compose.setContent { Stage {
            SensorIntroScreen(state, rememberCoroutineScope(), { granted }, { _, _, _, _ -> starts++ }, { requested = it })
        } }
        compose.onNodeWithText("이 미션은 위치 없이 측정할 수 있어요.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("움직임 측정 시작").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(card.exec_type, requested); assertEquals(0, starts); granted = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, starts); assertEquals(CardHomeStep.SENSOR_MEASURING, state.step.value) }
        capture("sensor-preparation")
    }

    @Test fun distanceDoesNotStartBeforeItsConsentIsKnown() {
        var starts = 0
        val state = CardHomeState().apply { revealedCard.value = card.copy(exec_type = "SENSOR_RUNNING_DISTANCE", state = "ACTIVE", target_value = 500, unit = "m") }
        compose.setContent { Stage { SensorIntroScreen(state, rememberCoroutineScope(), { true }, { _, _, _, _ -> starts++ }) } }
        compose.onNodeWithText("움직임 측정 시작").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.locationConsentLoaded.value = true }
        compose.onNodeWithText("움직임 측정 시작").performClick()
        compose.runOnIdle { assertEquals(0, starts); assertEquals(CardHomeStep.SENSOR_PERMISSION_FALLBACK, state.step.value) }
    }

    @Test fun manualCompletionFailureReturnsToConfirmationWithItsErrorVisible() {
        var manual = false
        val attempts = mutableListOf<String>()
        val api = java.lang.reflect.Proxy.newProxyInstance(com.tmtn.app.network.CardHomeApi::class.java.classLoader,
            arrayOf(com.tmtn.app.network.CardHomeApi::class.java)) { _, method, args ->
            check(method.name == "completeChallenge")
            manual = (args!![2] as com.tmtn.app.network.model.CompleteChallengeRequestBody).manual_check
            attempts += args[1] as String
            retrofit2.Response.error<Any>(503, okhttp3.ResponseBody.create(null, ""))
        } as com.tmtn.app.network.CardHomeApi
        val state = CardHomeState { api }.apply {
            revealedCard.value = card; setId.value = "offline-set"; step.value = CardHomeStep.CHALLENGE_CHECK_CONFIRM
        }
        compose.setContent { Stage { CardHomeFlow(state, { true }, onStartSensorTracking = { _, _, _, _ -> }, onStopSensorTracking = {}, onOpenSettings = {}) } }
        compose.onNodeWithText("네, 완료했어요").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(manual); assertEquals(CardHomeStep.CHALLENGE_CHECK_CONFIRM, state.step.value); assertFalse(state.isLoading.value) }
        compose.onNodeWithText("서버의 응답을 확인하지 못했어요.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("네, 완료했어요").performScrollTo().assertIsDisplayed()
        capture("manual-completion-error")
        compose.onNodeWithText("네, 완료했어요").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, attempts.size); assertEquals(attempts[0], attempts[1]) }
    }

    @Test fun completionWaitsForSyncAndKeepsMeasurementsOnFailure() {
        val holder = com.tmtn.app.sensor.SensorDataHolder
        holder.setServiceError(null); holder.setServiceRunning(true); holder.updateWalkingSeconds(600); holder.setSensorPaused(true)
        var stops = 0
        val state = CardHomeState().apply { revealedCard.value = card.copy(state = "ACTIVE"); step.value = CardHomeStep.SENSOR_MEASURING }
        try {
            compose.setContent { Stage { SensorMeasuringScreen(state, rememberCoroutineScope(), { stops++ }, {}, {}, { false }) } }
            compose.onNodeWithText("완료하기").performScrollTo().performClick()
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(0, stops); assertEquals(600, holder.walkingSeconds.value)
                assertEquals(CardHomeStep.SENSOR_MEASURING, state.step.value)
                assertNotNull(state.errorMessage.value)
            }
            compose.onNodeWithText("완료하기").assertIsEnabled()
        } finally { holder.resetAll(); holder.setSensorPaused(false) }
    }

    @Test fun profileFieldsStackAtLargeTextWithoutLosingRequiredInputs() {
        val state = com.tmtn.app.ui.onboarding.OnboardingState()
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.8f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { Stage { com.tmtn.app.ui.onboarding.A07ProfileScreen(state, rememberCoroutineScope()) } }
            }
        }
        compose.onNodeWithText("이름 · 필수").performScrollTo().performTextInput("지우")
        compose.onNodeWithText("별명 · 선택").performScrollTo().assertIsDisplayed()
        capture("profile-large-320")
        compose.onNodeWithText("몸무게 · 필수 (kg)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("평소 운동 알려주기").performScrollTo().assertIsNotEnabled()
    }

    @Test fun memoHttpFailureDoesNotPretendToSaveOrAdvance() {
        val api = java.lang.reflect.Proxy.newProxyInstance(com.tmtn.app.network.CardHomeApi::class.java.classLoader,
            arrayOf(com.tmtn.app.network.CardHomeApi::class.java)) { _, method, _ ->
            check(method.name == "updateDayMemo")
            retrofit2.Response.error<Any>(500, okhttp3.ResponseBody.create(null, ""))
        } as com.tmtn.app.network.CardHomeApi
        val state = CardHomeState { api }.apply { step.value = CardHomeStep.CHALLENGE_RETROSPECT }
        kotlinx.coroutines.runBlocking { state.submitRetrospect("다시 걷고 싶은 길") }
        assertEquals(CardHomeStep.CHALLENGE_RETROSPECT, state.step.value)
        assertNotEquals(true, state.hasMemoToday.value)
        assertNotNull(state.errorMessage.value)
        assertFalse(state.isLoading.value)
    }

    @Test fun repairedDamNewsKeepsItsDestinationReachableWithLargeText() {
        var opened = 0
        val pending = com.tmtn.app.network.model.StageUpPendingResponse(2, 3, "몸통 연결하기", 15, 7, 1, "나뭇가지")
        compose.setContent { Stage {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.8f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    com.tmtn.app.ui.dam.StageUpCelebrationScreen(pending, { opened++ }, {})
                }
            }
        } }
        compose.onNodeWithText("내 댐 보러 가기").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        compose.onNodeWithText("닫기").performScrollTo().assertIsDisplayed()
        capture("stage-up-large-320")
    }

    @Test fun openingDamAfterStageUpRestoresTheBottomNavigation() {
        val api = java.lang.reflect.Proxy.newProxyInstance(com.tmtn.app.network.CardHomeApi::class.java.classLoader,
            arrayOf(com.tmtn.app.network.CardHomeApi::class.java)) { _, method, _ ->
            check(method.name == "markStageUpSeen")
            retrofit2.Response.success(Unit)
        } as com.tmtn.app.network.CardHomeApi
        val state = CardHomeState(missionApiProvider = { api }).apply {
            setId.value = "offline-existing-set"
            step.value = CardHomeStep.STAGE_UP
            stageUpPending.value = com.tmtn.app.network.model.StageUpPendingResponse(2, 3, "몸통 연결하기", 15, 7, 1, "나뭇가지")
        }
        var dam by mutableStateOf(false)
        var immersive by mutableStateOf(false)
        compose.setContent { Stage {
            Column {
                Box(Modifier.weight(1f)) {
                    if (dam) androidx.compose.material3.Text("복구된 내 댐")
                    else CardHomeFlow(state, hasSensorPermissions = { true }, onStartSensorTracking = { _, _, _, _ -> },
                        onStopSensorTracking = {}, onOpenSettings = {}, onImmersiveChange = { immersive = it }, onOpenDam = { dam = true })
                }
                if (!immersive) com.tmtn.app.ui.nav.BottomNavBar(com.tmtn.app.ui.nav.MainTab.DAM, {})
            }
        } }
        compose.onNodeWithText("내 정보").assertDoesNotExist()
        capture("stage-up")
        compose.onNodeWithText("내 댐 보러 가기").performScrollTo().performClick()
        compose.onNodeWithText("복구된 내 댐").assertIsDisplayed()
        compose.onNodeWithText("댐").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("내 정보").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.advanceTimeBy(350)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "flow-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onNodeWithTag("flow-stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
