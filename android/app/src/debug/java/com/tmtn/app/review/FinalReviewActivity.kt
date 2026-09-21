package com.tmtn.app.review

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.cardhome.*
import com.tmtn.app.ui.common.TmtnExerciseFields
import com.tmtn.app.ui.journal.*
import com.tmtn.app.ui.profile.*
import com.tmtn.app.ui.reference.*
import com.tmtn.app.ui.theme.*
import retrofit2.Response

/** 예시 입력의 실제 모델 결과를 사용하는 오프라인 화면 검토. 릴리스에는 포함되지 않는다. */
class FinalReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { TMTNv1Theme { FinalReview(intent.getIntExtra("review_page", 0)) } }
    }
}

@Composable
private fun FinalReview(initialPage: Int) {
    var page by remember { mutableIntStateOf(initialPage.coerceIn(0, 4)) }
    val labels = listOf("홈", "운동 강도", "센서 미션", "일보·XAI", "기본 정보")
    val context = LocalContext.current
    val personal = remember {
        context.assets.open("journal_review/connected-personal.json").bufferedReader().use {
            Gson().fromJson(it.readText(), PersonalXaiResponse::class.java)
        }
    }
    val history = remember {
        context.assets.open("journal_review/weekly-history.synthetic.json").bufferedReader().use {
            Gson().fromJson(it.readText(), WeeklyXaiHistoryResponse::class.java)
        }
    }
    val editorial = remember {
        context.assets.open("journal_review/approved-editorial.json").bufferedReader().use {
            Gson().fromJson(it.readText(), JournalEditorialResponse::class.java)
        }
    }
    val card = remember { CardRevealResponse("review-card", "SELF_CHECK", "해낸 일 표시하기",
        "오늘 해낸 일을 표시해 보세요.", "METAL", "마음", 1, "가지", "READY",
        fortune_text = "해낸 것을 보면 자신감의 운이 커져요.", lucky_location = "집", line_text = "오늘 해낸 일 1가지 표시하기!") }
    val home = remember { CardHomeState(serviceDateProvider = { "2026-09-20" }, missionApiProvider = { error("화면 검토에서는 미션을 저장하지 않아요.") }, waist = WaistEstimateState {
        Response.success(listOf(PredictionResultResponse("WAIST_CM_ESTIMATE", "85.5".toBigDecimal(), "COMPUTED")))
    }).apply {
        drawState.value = "SELECTED"; todayChallengeId.value = card.challenge_id
        revealedCard.value = card; todayChallengeState.value = "READY"; cardServiceDate.value = "2026-09-20"
    } }
    val week = remember { WeeklyReportResponse("2026-09-14", "2026-09-20", (14..20).map {
        CalendarDayItem("2026-09-$it", when (it) { 14, 17 -> "COMPLETED"; 16 -> "REST"; 20 -> "FUTURE"; else -> "INCOMPLETE" })
    }, 2, 7, null, emptyList()) }
    val today = remember { JournalLoad.Ready(JournalToday(
        CardWindowResponse("SELECTED", "2026-09-19", "review", emptyList(), "option", "review-card", "READY"), card)) }
    Column(Modifier.fillMaxSize().background(ColorBackground).statusBarsPadding().navigationBarsPadding()) {
        Text("예시 데이터 · 화면 검토", style = TmtnType.caption, color = ColorOnSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            labels.forEachIndexed { index, label -> TextButton(onClick = { page = index }, modifier = Modifier.testTag("review-tab-$index")) {
                Text(label, style = TmtnType.label, color = if (page == index) ColorPrimary else ColorOnSurfaceVariant)
            } }
        }
        Box(Modifier.weight(1f)) {
            when (page) {
                0 -> Column {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        listOf("뽑기 전", "선택 후", "완료", "쉼", "중단", "포기", "긴 문구", "운동 1회", "운동 2회", "운동 중", "운동 멈춤").forEachIndexed { index, label ->
                            TextButton(onClick = {
                                home.drawState.value = if (index == 0) "NONE" else "SELECTED"
                                home.todayChallengeState.value = when (index) { 2, 7, 8, 9, 10 -> "COMPLETED"; 4 -> "PAUSED"; 5 -> "SKIPPED"; else -> "READY" }
                                home.homeExerciseProgress.value = if (index in listOf(2, 7, 8, 9, 10)) {
                                    val used = when (index) { 7 -> 1; 8 -> 2; else -> 0 }
                                    HomeExerciseProgress("2026-09-20", used, 2 - used)
                                } else null
                                home.activeExerciseSession.value = if (index in listOf(9, 10)) ExerciseMissionSessionResponse(
                                    java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                                    if (index == 10) "PAUSED" else "ACTIVE", "SENSOR_WALKING_DURATION",
                                    "천천히 걷기", "WOOD", "나뭇가지", 300, null, 72, 0, null) else null
                                home.isTodayRestDay.value = index == 3
                                home.isTodayGivenUp.value = index == 5
                                home.revealedCard.value = if (index == 6) card.copy(
                                    title = "오늘 해낸 작은 실천을 천천히 떠올리고 기록하기",
                                    fortune_text = "작은 실천 하나하나를 돌아보면 내일 다시 시작할 자신감이 차곡차곡 쌓여요.",
                                    lucky_location = "마음이 편안한 집 안의 조용한 자리",
                                    line_text = "오늘 해낸 일을 하나 떠올리고 내가 나에게 해주고 싶은 말을 적어보세요."
                                ) else card
                            }, modifier = Modifier.testTag("review-home-$index")) { Text(label, style = TmtnType.caption) }
                        }
                    }
                    CardHomeScreen(home, rememberCoroutineScope(), onOpenTuntunScore = { page = 3 }, showDebugTools = false)
                }
                1 -> {
                    var low by remember { mutableIntStateOf(30) }; var moderate by remember { mutableIntStateOf(50) }; var high by remember { mutableIntStateOf(50) }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                        TmtnExerciseFields(0, {}, null, {}, low, { low = it }, moderate, { moderate = it }, high, { high = it }, strengthContent = {})
                    }
                }
                2 -> {
                    var phase by remember { mutableStateOf(SensorJourneyPhase.MOVING) }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("어깨 펴고 걷기", style = TmtnType.title, color = ColorOnSurface)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            SensorJourneyPhase.entries.map { it to when (it) {
                                SensorJourneyPhase.MOVING -> "인식"
                                SensorJourneyPhase.WAITING -> "대기"
                                SensorJourneyPhase.PAUSED -> "일시정지"
                                SensorJourneyPhase.PREPARING -> "준비"
                                SensorJourneyPhase.SIGNAL -> "GPS"
                                SensorJourneyPhase.COMPLETE -> "목표 달성"
                                SensorJourneyPhase.ERROR -> "오류"
                            } }.forEach { (value, label) ->
                                TextButton(onClick = { phase = value }, modifier = Modifier.testTag("review-motion-${value.name}")) { Text(label) }
                            }
                        }
                        val complete = phase == SensorJourneyPhase.COMPLETE
                        SensorJourneyPanel(SensorDisplay(if (complete) "05:00" else "01:30", "목표 05:00",
                            if (complete) 1f else .3f, phase == SensorJourneyPhase.MOVING), phase,
                            "나뭇가지", if (complete) "목표의 100%" else "목표의 30%")
                    }
                }
                3 -> JournalScreen(JournalLoad.Ready(week), JournalLoad.Ready(emptyList()), today, null,
                    WaistEstimateUi.Unavailable, false, {}, { page = 0 }, { page = 4 }, {},
                    personal = JournalLoad.Ready(personal), history = JournalLoad.Ready(history),
                    editorial = JournalLoad.Ready(editorial),
                    companion = JournalLoad.Ready(CompanionResponse(2, 12, 20, 8, emptyList(), emptyList())))
                4 -> {
                    val profile = remember { ProfileState(profileApiProvider = { error("화면 검토에서는 정보를 저장하지 않아요.") }).apply {
                        userInfo.value = UserInfoResponse(900000001, "틈튼", "틈튼이", "review@example.invalid", null, 1991, 1, "MALE", null, "2026-09-14T00:00:00Z")
                    } }
                    BasicInfoScreen(profile, rememberCoroutineScope()) { page = 0 }
                }
            }
        }
    }
}
