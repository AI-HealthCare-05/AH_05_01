package com.tmtn.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tmtn.app.review.FinalReviewActivity
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 실제 화면 컴포넌트를 예시 데이터로 열어 탐색·가독성·XAI 연결을 확인한다. */
class FinalHandoffUiTest {
    @get:Rule val compose = createAndroidComposeRule<FinalReviewActivity>()

    @Test fun homeCStatesKeepMissionActionsTogether() {
        compose.onNodeWithTag("review-home-0").performClick()
        compose.onNodeWithTag("home-before-draw").assertIsDisplayed()
        val beforeHeight = compose.onNodeWithTag("home-before-draw").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        compose.onNodeWithTag("home-extra").assertIsNotEnabled()
        capture("home-c-01-before-draw")
        compose.onNodeWithTag("review-home-1").performClick()
        val cardBounds = compose.onNodeWithTag("home-selected-card").getUnclippedBoundsInRoot()
        val actions = compose.onNodeWithTag("home-mission-actions").getUnclippedBoundsInRoot()
        val extra = compose.onNodeWithTag("home-extra").getUnclippedBoundsInRoot()
        assertEquals("기본 카드 높이 일치", beforeHeight, (cardBounds.bottom - cardBounds.top).value, 8f)
        assertEquals(8f, (actions.top - cardBounds.bottom).value, 1f)
        assertEquals(32f, (extra.top - actions.bottom).value, 1f)
        capture("home-c-02-selected")
        compose.onNodeWithTag("review-home-2").performClick()
        compose.onNodeWithTag("home-completion").assertIsDisplayed()
        compose.onNodeWithTag("home-selected-card").assertDoesNotExist()
        compose.onNodeWithText("오늘도 고생했어!").assertIsDisplayed()
        compose.onNodeWithText("완료한 카드 보기").assertExists()
        compose.onNodeWithText("쉬어가기").assertDoesNotExist()
        compose.onNodeWithTag("home-extra").performScrollTo().assertIsEnabled().assertHasClickAction()
        compose.onNodeWithText("열림").assertExists()
        capture("home-c-03-completed")
        compose.onNodeWithTag("review-home-3").performClick()
        compose.onNodeWithText("고른 미션 도전하기").assertExists()
        compose.onNodeWithTag("home-extra").assertIsNotEnabled()
        compose.onNodeWithTag("review-home-4").performClick()
        compose.onNodeWithText("미션 이어서 하기").assertExists()
        compose.onNodeWithTag("review-home-5").performClick()
        compose.onNodeWithText("고른 미션 도전하기").assertExists()
        compose.onNodeWithText("오늘은 여기까지. 원하면 다시 도전할 수 있어요.").assertExists()
    }

    @Test fun completedHomeCheersForOneAndTwoExtraExercises() {
        compose.onNodeWithTag("review-home-2").performScrollTo().performClick()
        compose.onNodeWithText("틈새운동도 가볍게 도전해볼래?\n오늘은 여기까지여도 충분해.").assertIsDisplayed()
        capture("home-c-05-card-cheer")
        compose.onNodeWithTag("review-home-7").performScrollTo().performClick()
        compose.onNodeWithText("틈새운동까지 한 번 해냈네!\n한 번 더 해봐도 좋고, 쉬어도 좋아.").assertIsDisplayed()
        capture("home-c-06-extra-one")
        compose.onNodeWithTag("review-home-8").performScrollTo().performClick()
        compose.onNodeWithTag("home-selected-card").assertDoesNotExist()
        compose.onNodeWithText("오늘의 실천, 모두 해냈어!").assertIsDisplayed()
        compose.onNodeWithText("내 댐 보러 가기").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("완료한 카드 보기").assertExists()
        capture("home-c-07-extra-two")
    }

    @Test fun exerciseReturnBannerHasBalancedPaddingAndCorrectStatus() {
        compose.onNodeWithTag("review-home-9").performScrollTo().performClick()
        compose.onNodeWithTag("home-exercise-return").performScrollTo()
        val banner = compose.onNodeWithTag("home-exercise-return").getUnclippedBoundsInRoot()
        val label = compose.onNodeWithTag("home-exercise-return-label", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((label.top - banner.top).value, (banner.bottom - label.bottom).value, 1f)
        compose.onNodeWithText("틈새 운동 진행 중 · 돌아가기").assertIsDisplayed()
        capture("home-c-08-return-banner")
        compose.onNodeWithTag("review-home-10").performScrollTo().performClick()
        compose.onNodeWithText("틈새 운동 일시정지 · 돌아가기").assertIsDisplayed()
        capture("home-c-09-return-paused")
    }

    @Test fun newspaperBeaverSharesTheDamSceneInsteadOfSittingBesideIt() {
        tab(3)
        compose.onNodeWithTag("journal-dam-scene").performScrollTo()
        compose.onNodeWithText("지금의 내 댐").assertExists()
        compose.onNodeWithContentDescription("현재 내 댐 2단계").assertIsDisplayed()
        val dam = compose.onNodeWithTag("journal-dam-art", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val beaver = compose.onNodeWithTag("journal-dam-beaver", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(beaver.left < dam.right && beaver.right > dam.left)
        assertTrue(beaver.top < dam.bottom && beaver.bottom > dam.top)
        capture("home-c-10-journal-scene")
    }

    @Test fun homeCLongCsvFieldsGrowWithoutEllipsis() {
        val normal = compose.onNodeWithTag("home-selected-card").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        compose.onNodeWithTag("review-home-6").performScrollTo().performClick()
        val expanded = compose.onNodeWithTag("home-selected-card").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        assertTrue(expanded > normal)
        compose.onNodeWithText("오늘 해낸 일을 하나 떠올리고 내가 나에게 해주고 싶은 말을 적어보세요.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이 행동 시작하기").performScrollTo().assertIsDisplayed()
        capture("home-c-04-long-csv")
    }

    @Test fun homeCardsAndWaistDetailsRemainReachable() {
        compose.onNodeWithText("해낸 일 표시하기").assertIsDisplayed()
        capture("01-home-mission")
        compose.onNodeWithText("85.5 cm").performScrollTo().assertIsDisplayed()
        capture("02-home-sections")
        compose.onNodeWithContentDescription("줄자 눈금, 추정 허리둘레 85.5 센티미터 위치")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("home-waist").performScrollTo()
        capture("02b-home-waist")
        compose.onNodeWithTag("home-waist").performScrollTo().performClick()
        compose.onNodeWithText("설명 접기").performScrollTo().assertIsDisplayed()
    }

    @Test fun intensityAndBirthLabelsAreReadable() {
        tab(1)
        compose.onNodeWithText("저강도").assertIsDisplayed()
        compose.onNodeWithText("중강도").assertIsDisplayed()
        capture("03-intensity")
        compose.onNodeWithText("고강도").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("aerobic-level-3").performScrollTo()
        capture("04-intensity-high")
        tab(4)
        compose.onNodeWithText("태어난 연·월").assertIsDisplayed()
        compose.onNodeWithText("생년월").assertDoesNotExist()
        capture("05-birth-label")
    }

    @Test fun sensorImagesMatchStateAndStayStill() {
        tab(2)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeByFrame()
        val first = compose.onNodeWithTag("sensor-companion").captureToImage().asAndroidBitmap()
        var pausedImage: Bitmap? = null
        val states = listOf(
            "MOVING" to "움직임 인식 중", "WAITING" to "움직임을 기다리고 있어요",
            "PAUSED" to "일시정지됨", "PREPARING" to "측정을 준비하고 있어요",
            "SIGNAL" to "GPS 신호를 찾고 있어요", "COMPLETE" to "목표를 채웠어요",
            "ERROR" to "측정을 확인해 주세요", "MOVING" to "움직임 인식 중",
        )
        states.forEach { (state, label) ->
            // 상태 선택을 위한 스크롤은 완료시킨 뒤 정지 이미지의 시간 경과를 검사한다.
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("review-motion-$state").performScrollTo().performClick()
            compose.onNodeWithText(label).assertIsDisplayed()
            compose.mainClock.autoAdvance = false
            val description = when (state) {
                "PAUSED" -> "잠깐 쉬고 있는 틈튼 비버"
                "COMPLETE" -> "목표 달성을 축하하는 틈튼 비버"
                else -> "운동을 응원하는 틈튼 비버"
            }
            compose.onNodeWithTag("sensor-companion")
                .assertContentDescriptionEquals(description)
            val current = compose.onNodeWithTag("sensor-companion").captureToImage().asAndroidBitmap()
            when (state) {
                "PAUSED" -> {
                    assertFalse("일시정지는 응원과 다른 쉬는 그림이어야 한다", first.sameAs(current))
                    pausedImage = current
                    compose.onNodeWithText("01:30").assertIsDisplayed()
                }
                "COMPLETE" -> {
                    assertFalse("달성은 응원과 다른 축하 그림이어야 한다", first.sameAs(current))
                    assertNotNull(pausedImage)
                    assertFalse("달성과 일시정지 그림은 달라야 한다", current.sameAs(pausedImage!!))
                    compose.onNodeWithText("05:00").assertIsDisplayed()
                    compose.onNodeWithText("100% 달성").assertExists()
                }
                else -> assertTrue("나머지 상태와 재개는 응원 그림이어야 한다: $state", first.sameAs(current))
            }
            compose.mainClock.advanceTimeBy(1200)
            assertTrue("응원 그림은 시간에 따라 움직이지 않는다: $state", current.sameAs(
                compose.onNodeWithTag("sensor-companion").captureToImage().asAndroidBitmap()))
            if (state == "MOVING") capture("06-walking")
            if (state == "WAITING") capture("07-waiting")
            if (state == "PAUSED") capture("12-paused")
            if (state == "COMPLETE") capture("13-complete")
        }
        compose.onNodeWithText("01:30").assertIsDisplayed()
        compose.onNodeWithText("30% 달성").assertExists()
        compose.mainClock.autoAdvance = true
    }

    @Test fun mondayWeekAndVerifiedXaiAreConnected() {
        tab(3)
        compose.onNodeWithTag("journal-week-dates").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("9월 14일, 실천").assertExists()
        compose.onNodeWithContentDescription("9월 20일, 다가올 날").assertIsNotEnabled()
        capture("08-monday-week")
        compose.onNodeWithTag("journal-open-personal").performClick()
        compose.onNodeWithTag("personal-activity-comparison").assertExists()
        capture("09-xai-comparison")
        compose.onNodeWithTag("personal-shap-toggle").performScrollTo().performClick()
        compose.onNodeWithText("내 정보가 반영된 방향").performScrollTo().assertIsDisplayed()
        capture("10-xai-calculation")
        compose.onNodeWithText("계산 근거 자세히 보기").performScrollTo().assertIsDisplayed()
        capture("11-xai-contributions")
    }

    @Test fun weeklyHistoryAndApprovedReadingAreVisible() {
        tab(3)
        compose.onNodeWithText("지난주와 이번 주의 실천").performScrollTo().assertIsDisplayed()
        capture("14-weekly-practice")
        compose.onNodeWithTag("weekly-score-chart").performScrollTo().assertIsDisplayed()
        capture("15-weekly-score-chart")
        compose.onNodeWithTag("week-2026-09-07").performScrollTo().performClick()
        compose.onNodeWithTag("week-2026-09-07").assertIsSelected()
        compose.onNodeWithText("9/7 — 9/13").assertExists()
        compose.onNodeWithTag("weekly-score-delta").performScrollTo().assertIsDisplayed()
        capture("16-weekly-comparison")
        compose.onNodeWithTag("week-2026-08-24").performScrollTo().performClick()
        compose.onNodeWithText("이 주에는 저장된 참고점수가 없어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("weekly-score-delta").assertDoesNotExist()
        compose.onNodeWithText("생활 읽을거리").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("출처와 적용 범위 보기").onFirst().performScrollTo().assertIsDisplayed()
        capture("17-approved-reading")
    }

    private fun tab(index: Int) { compose.onNodeWithTag("review-tab-$index").performScrollTo().performClick() }
    private fun capture(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // 시스템 창 애니메이션은 Compose 테스트 시계와 독립적이다.
        android.os.SystemClock.sleep(350)
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "final-handoff")
        root.mkdirs()
        // 바텀시트도 포함하도록 실제 창 화면을 캡처한다.
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(root, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
