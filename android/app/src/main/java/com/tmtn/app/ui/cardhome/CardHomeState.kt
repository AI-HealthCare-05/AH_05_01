package com.tmtn.app.ui.cardhome

import com.tmtn.app.ui.common.failWithMessage
import com.tmtn.app.ui.common.userMessageOr
import androidx.compose.runtime.mutableStateOf
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.CalendarDayItem
import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.network.model.CompleteChallengeRequestBody
import com.tmtn.app.network.model.CompleteExerciseMissionSessionRequest
import com.tmtn.app.network.model.CompleteExerciseMissionSessionResponse
import com.tmtn.app.network.model.CreateExerciseMissionSessionRequest
import com.tmtn.app.network.model.ExerciseMissionActionRequest
import com.tmtn.app.network.model.ExerciseMissionOption
import com.tmtn.app.network.model.ExerciseMissionSessionResponse
import com.tmtn.app.network.model.ExerciseMissionsTodayResponse
import com.tmtn.app.network.model.MaterialItem
import com.tmtn.app.network.model.MemoUpdateRequest
import com.tmtn.app.network.model.RestDayRequest
import com.tmtn.app.ui.common.currentServiceDateString
import com.tmtn.app.ui.common.isoDateToKoreanLabel
import com.tmtn.app.ui.onboarding.parseErrorMessage
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// ⚠️ 2026-09-17 추가(QA F13) - 틈새 운동 완료 저장 상태.
enum class ExerciseSaveState { IDLE, SAVING, FAILED }

enum class CardHomeStep {
    LOADING,       // B08
    HOME,          // B01 / B01b (draw_state로 구분)
    DECK_PICK,     // B03 / B04 / B05 (로컬 상태로 구분)
    REVEALED,      // B06
    COMPLETED,     // B07
    ERROR,         // B09
    REASON_DETAIL,        // B10
    ALTERNATIVE_REQUEST,  // B11
    ALTERNATIVE_APPLIED,  // B12
    NOTIFICATION_INBOX,   // B13/B15
    REST_DAY_DONE,    // B17 (지금은 도달 경로 없음 - confirmRestDay가 HOME으로 바로 보냄)
    CHALLENGE_CHECK,          // C01 (체크형)
    CHALLENGE_CHECK_CONFIRM,  // C01b (신규) - "완료하기" 누른 뒤 실제 완료 처리 전 자가진단 확인
    CHALLENGE_TIMER_START,    // C02 (타이머형 · 시작 전)
    CHALLENGE_TIMER_RUNNING,  // C03 (타이머형 · 진행 중)
    CHALLENGE_TIMER_PAUSED,   // C04 (타이머형 · 일시정지)
    CHALLENGE_PROCESSING,     // C05 (완료 처리 중)
    CHALLENGE_RETROSPECT,     // C20 (한 줄 회고) - 저장/건너뛰기 후 COMPLETED로 이동
    STAGE_UP,                 // G07 (댐 단계 상승 축하) - 완료 직후 단계가 올랐으면 여기부터
    SENSOR_INTRO,             // C09 (자동 측정 공통 안내)
    SENSOR_MEASURING,         // C10/C12/C14 통합 (측정 중 - exec_type별로 표시만 다름)
    SENSOR_PERMISSION_FALLBACK, // C16 (권한 거부 · 미지원)
    SENSOR_RESULT,            // C18 (자동 측정 완료 결과)

    // ⚠️ 2026-09-11 추가 - 틈새 운동(TMtn_UI_V17 §5). "오늘의 카드" 완료 후에만 진입 가능.
    EXTRA_LIST,       // B31 (오늘 고를 수 있는 틈새 운동 목록)
    EXTRA_DETAIL,     // B32/B43 (선택한 운동 상세 · 시작 확인)
    EXTRA_RUNNING,    // B34~B37 (측정 중 - 기존 SENSOR_MEASURING과 같은 표시 규칙 재사용)
    EXTRA_REWARD,     // B38/B41 (완료 · 재료 획득)
}

/**
 * 오늘의 카드 흐름 전체(B01~B09) 상태.
 * Navigation Compose 없이 온보딩과 동일한 방식(상태값으로 화면 전환)으로 구현.
 */
class CardHomeState(
    private val serviceDateProvider: suspend () -> String = { currentServiceDateString() },
    val waist: com.tmtn.app.ui.reference.WaistEstimateState = com.tmtn.app.ui.reference.WaistEstimateState(),
    private val peerScoreEnabled: Boolean = com.tmtn.app.BuildConfig.PEER_SCORE_ENABLED,
    private val missionApiProvider: () -> com.tmtn.app.network.CardHomeApi = { ApiClient.cardHomeApi },
) {
    var step = mutableStateOf(CardHomeStep.LOADING)
    var isLoading = mutableStateOf(false)
    var errorMessage = mutableStateOf<String?>(null)
    private var completionAttempt: Pair<String, String>? = null

    // 오늘의 카드 세트
    var setId = mutableStateOf<String?>(null)
    var cardServiceDate = mutableStateOf<String?>(null)
    private var homeReturnRefreshRunning = false
    var optionIds = mutableStateOf<List<String>>(emptyList())
    var drawState = mutableStateOf<String?>(null) // "AWAITING_SELECTION" / "SELECTED"
    var todayChallengeState = mutableStateOf<String?>(null) // "READY"/"ACTIVE"/"PAUSED"/"COMPLETED"/"SKIPPED"
    var todayChallengeId = mutableStateOf<String?>(null) // SELECTED일 때만 채워짐 - "미션 이어하기"용
    // B07: 오늘 회고(메모)를 이미 남겼는지 - null=아직 확인 전, true/false=확인됨.
    // "한 줄 남기기" 버튼을 보여줄지 판단하는 용도 (CompletedScreen에서 checkTodayMemo로 채움).
    var hasMemoToday = mutableStateOf<Boolean?>(null)

    // B03~B05: 고르기 전(null) -> 가운데로 고름(index) -> 확정 다이얼로그(showConfirmDialog)
    var pickedIndex = mutableStateOf<Int?>(null)
    var showConfirmDialog = mutableStateOf(false)

    // B06: 확정 후 공개된 카드
    var revealedCard = mutableStateOf<CardRevealResponse?>(null)

    // B07: 완료 결과
    var pointsAwarded = mutableStateOf(0)
    // ⚠️ 2026-09-08 QA(N10) 반영: 완료 순간(회고 화면까지)에 "받침돌 1개를 얻었어" 같은
    // 보상 정보가 아무 데도 안 보이고, 홈에 도착해서야 재료가 늘어난 걸 볼 수 있었음.
    // completeChallenge() 응답에 이미 five_element가 실려 오는데 안 쓰고 있었음 - 저장해서
    // RetrospectScreen에서 바로 보여줌.
    var awardedFiveElement = mutableStateOf<String?>(null)

    // 댐(재료) 요약 - B01/B01b 상단에 표시
    val cardEntryRequested = mutableStateOf(false)

    var companionStage = mutableStateOf(0)
    var companionLoaded = mutableStateOf(false)
    var companionMaterialsNeeded = mutableStateOf<Int?>(null)
    var companionNextStageLabel = mutableStateOf<String?>(null)
    // 홈 화면 "댐" 요약 행의 오른쪽 빈 공간에 재료 개수를 보여주기 위한 값 (RecentSummaryListCard 참고).
    var companionMaterials = mutableStateOf<List<MaterialItem>>(emptyList())
    // ⚠️ 2026-09-08 QA(9번) 반영: 기본값 true - 아직 동의 목록을 못 불러온 순간(홈 로딩
    // 직후 잠깐)에 실수로 센서 미션을 막아버리지 않기 위함. 실제로 거부한 게 확인되면
    // loadLocationConsent()가 false로 갱신함.
    var locationConsentGranted = mutableStateOf(false)
    var locationConsentLoaded = mutableStateOf(false)
    var sensorFallbackReason = mutableStateOf("PERMISSION")

    // ⚠️ 홈 "틈튼지수" 요약 카드 - 예전엔 "68"/"보통 구간" 등이 전부 하드코딩된 예시였음.
    // /tuntun-score/peer/v2의 peerCompositeScore를 읽음. 종합 점수를 비교 순위로 변환하지 않음.
    var tuntunIndexValue = mutableStateOf<Int?>(null)
    var tuntunIndexPresentationValue = mutableStateOf<Double?>(null)
    // ⚠️ PR #12 리뷰(P0) 반영: 홈 카드가 배지 없이 상수 Mock 점수를 그대로 보여주고 있었음.
    var tuntunIndexIsMock = mutableStateOf(false)

    // ⚠️ 홈 "최근 7일" 도트 - 예전엔 listOf(true, true, false, true, true, false, null)로
    // 항상 똑같은 예시 패턴만 보여줬음. 기록 탭의 주간 리포트 API(최근 7일 실제 상태)를 그대로 씀.
    var recentWeek = mutableStateOf<List<CalendarDayItem>>(emptyList())

    // ⚠️ 테스트 전용 - "다음 날로" 눌렀을 때 서버가 지금 인식하는 시뮬레이션 날짜 표시용.
    var debugSimulatedToday = mutableStateOf<String?>(null)

    // ⚠️ 2026-09-08 QA(N11) 반영: 화면 상단 날짜 캡션이 전부 LocalDate.now()(기기의 진짜
    // 오늘)를 그대로 썼음 - 시뮬레이션으로 날짜를 밀어도 상단은 계속 원래 날짜로 남아서,
    // 서버는 미래를 보고 있는데 화면은 다른 날을 보여주는 모순이 있었음(디버그 기능
    // 전용이지만 QA 중 계속 혼란을 줌). 시뮬레이션 값이 있으면 그걸 파싱해서 쓰고, 없으면
    // (null 또는 "-") 기존처럼 기기 오늘을 씀.
    fun displayDateLabel(): java.time.LocalDate {
        cardServiceDate.value?.let { date ->
            runCatching { java.time.LocalDate.parse(date) }.getOrNull()?.let { return it }
        }
        val simulated = debugSimulatedToday.value
        if (simulated != null && simulated != "-") {
            runCatching { java.time.LocalDate.parse(simulated) }.getOrNull()?.let { return it }
        }
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
    }

    // B16/B17: 쉬어가기
    var restDaysUsedThisWeek = mutableStateOf(0)
    var restDaysRemainingThisWeek = mutableStateOf(2)
    var currentStreak = mutableStateOf(0)
    var isTodayRestDay = mutableStateOf(false)
    // ⚠️ 2026-09-07 반영: G3(카드 미선택 상태의 포기, B19) - daily_record_notes.is_given_up.
    var isTodayGivenUp = mutableStateOf(false)
    // B16: "오늘 쉬어가기" 시트를 지금 화면 위에 띄울지 - 더 이상 별도 step이 아니라
    // D그룹 DayDetailSheet와 같은 오버레이 방식(RestDaySheetScreen 오버레이 참고).
    var showRestDaySheet = mutableStateOf(false)
    // ⚠️ 2026-09-07 반영: 상태전이 정책 REST<->GIVE_UP 전환 시트 2종(TransitionSheets.kt)
    // + C26(포기 확인) - 전부 showRestDaySheet와 같은 오버레이 방식.
    var showRestCancelSheet = mutableStateOf(false)
    var showRestToGiveUpSheet = mutableStateOf(false)
    var showGiveUpConfirmSheet = mutableStateOf(false)

    // C그룹: 챌린지 진행 (타이머형)
    var timerElapsedSeconds = mutableStateOf(0)
    var timerIsPaused = mutableStateOf(false)
    var showQuitDialog = mutableStateOf(false)
    // ⚠️ 2026-09-08 QA(N6) 반영: 앱 시작 시점에 권한을 미리 요청하던 걸 없애고, 센서
    // 미션 "시작하기"를 처음 눌렀을 때로 미룸(SensorChallengeScreens.kt 참고) - 이 화면
    // 안에서 "아직 시스템 다이얼로그를 안 띄워봤는지"를 구분하려고 씀. 한 번 띄워본 뒤에도
    // 여전히 권한이 없으면(거부됨) 그때 폴백 화면으로 안내함.
    var hasRequestedSensorPermissionsOnce = mutableStateOf(false)
    // ⚠️ 2026-09-08 QA(N9) 반영: CHECK형 전용 "중단" 확인 - TIMER형(showQuitDialog)과
    // 분리해서 문구를 다르게 씀(ChallengeTimerScreens.kt의 CheckGiveUpDialog 참고).
    var showCheckGiveUpDialog = mutableStateOf(false)

    // G07: 완료 직후 단계 상승 여부
    var stageUpPending = mutableStateOf<com.tmtn.app.network.model.StageUpPendingResponse?>(null)

    /** 완료 관련 함수들이 직접 COMPLETED로 안 가고 항상 이걸 거침 - 단계가 올랐으면
     * G07(STAGE_UP)을 먼저 보여주고, 아니면 그대로 COMPLETED로. */
    private suspend fun goToCompletedOrCelebrate() {
        // ⚠️ 2026-09-06 QA(P1-4) 반영: "한 줄 남기기"(또는 건너뛰기) 이후 REVEALED로
        // 보냈는데, 그 화면엔 축하·재료 획득·연속 기록 표시가 없어서 "완료했는데 아무
        // 일도 안 일어난다"는 지적을 받음. 그 정보는 이미 홈 화면이 정확하게 보여주고
        // 있으니(연속 기록·최근 7일·재료·댐 진행 전부 정상 동작 확인됨), 회고를 마치면
        // 곧바로 홈으로 보내서 그 축하를 사용자가 직접 나가서 찾지 않아도 되게 함.
        revealedCard.value = revealedCard.value?.copy(state = "COMPLETED")
        val pending = try { missionApiProvider().getStageUpPending().let { if (it.isSuccessful) it.body() else null } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        if (pending != null) {
            stageUpPending.value = pending
            step.value = CardHomeStep.STAGE_UP
        } else {
            loadToday()
        }
    }

    // The seen receipt does not grant a reward; keep the news visible if this request fails.
    suspend fun acknowledgeStageUp(openDam: (() -> Unit)? = null) {
        if (isLoading.value) return
        isLoading.value = true
        errorMessage.value = null
        try {
            val response = missionApiProvider().markStageUpSeen()
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            companionStage.value = stageUpPending.value?.new_stage ?: companionStage.value
            stageUpPending.value = null
            if (openDam != null) {
                // Refresh home next time it is entered, without delaying the destination behind extra GETs.
                setId.value = null
                step.value = CardHomeStep.HOME
                openDam()
            } else loadToday()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            errorMessage.value = e.userMessageOr("복구 소식을 확인하지 못했어요. 다시 눌러 주세요.")
        } finally { isLoading.value = false }
    }

    suspend fun loadToday() {
        step.value = CardHomeStep.LOADING
        errorMessage.value = null
        runCatching {
            val response = ApiClient.cardHomeApi.getTodayCards()
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { window ->
            applyHomeWindow(window)
            // ⚠️ 2026-09-07 반영: G3(카드 미선택 상태의 포기, B19) - daily_record_notes.
            // is_given_up이 카드 재조회 응답(CardWindowResponse)에 이미 노출돼 있었는데
            // 여기서 안 받아오고 있었음.
            // ⚠️ 쉬어가기 상태가 daily_record_notes에만 있고 카드 재조회 응답엔 없어서,
            // 앱을 재시작하거나 다른 탭 갔다 오면 쉬어가기 표시가 사라지던 버그를 고침 —
            // isTodayRestDay만 반영하고, 실제 화면 표시는 CardHomeScreen(홈)이 그 안에서
            // 마스코트 카드만 "쉼" 버전으로 바꿔서 보여줌(틈튼지수 요약 등 나머지는 그대로 유지).
            step.value = CardHomeStep.HOME
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) { isLoading.value = false; throw e }
            errorMessage.value = e.userMessageOr("오늘의 카드를 가져오지 못했어요.")
            step.value = CardHomeStep.ERROR
        }
        // ⚠️ PR #12 리뷰(P1) 반영: 이 4개가 순차 suspend 호출이라 홈 진입이 왕복 5회
        // 직렬(ngrok 경유면 체감됨)이었음 - 서로 의존관계가 없어서 coroutineScope로
        // 묶어 동시에 보내면 가장 오래 걸리는 1개 시간만큼만 걸림.
        coroutineScope {
            launch {
                val id = todayChallengeId.value
                if (id == null) revealedCard.value = null else {
                    if (revealedCard.value?.challenge_id != id) revealedCard.value = null
                    try {
                        val response = ApiClient.cardHomeApi.revealChallenge(id)
                        if (response.isSuccessful && todayChallengeId.value == id) revealedCard.value = response.body()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { /* The existing card action remains available for retry. */ }
                }
            }
            launch { loadCompanion() }
            launch { loadTuntunIndexSummary() }
            launch { loadRecentWeek() }
            // ⚠️ 2026-09-08 QA(9번) 반영: "위치정보 수집·이용 동의"(LOCATION_DATA_USAGE,
            // 선택 동의)를 거부해도 센서 미션(걷기·달리기 등)이 그대로 활성화돼 있었음 -
            // 동의 여부를 홈 로딩 시 같이 조회해서 SensorIntroScreen이 참고하게 함.
            launch { loadLocationConsent() }
            // ⚠️ 2026-09-04 QA(P0-4) 반영: currentStreak가 여기서 채워지는 게 아니라 "오늘
            // 쉬어가기" 바텀시트를 열 때만(openRestDaySheet) 채워지고 있었음. 그래서 홈의
            // "연속 기록"이 항상 초기값 0으로만 보이고, 기록 탭·내 정보 탭(각자 따로 조회)과
            // 어긋났음. 홈 로드 시에도 정확한 값을 받아오게 함.
            launch { loadStreak() }
        }
    }

    /** A newspaper CTA resolves today's destination without starting or resuming any mission. */
    suspend fun openCardFromJournal() {
        if (isLoading.value) return
        isLoading.value = true
        errorMessage.value = null
        step.value = CardHomeStep.LOADING
        try {
            val response = missionApiProvider().getTodayCards()
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            val window = response.body() ?: failWithMessage("오늘의 카드를 확인하지 못했어요.")
            applyHomeWindow(window)
            val destination = journalCardDestination(window.is_rest_day || window.is_given_up, window.challenge_state, window.challenge_id)
            if (destination == CardHomeStep.REVEALED) {
                val cardResponse = missionApiProvider().revealChallenge(window.challenge_id!!)
                if (!cardResponse.isSuccessful) failWithMessage(parseErrorMessage(cardResponse))
                revealedCard.value = cardResponse.body() ?: failWithMessage("카드 내용을 확인하지 못했어요.")
            }
            step.value = destination
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            errorMessage.value = error.userMessageOr("오늘의 카드를 확인하지 못했어요. 다시 시도해 주세요.")
            step.value = CardHomeStep.ERROR
        } finally { isLoading.value = false }
    }

    private fun applyHomeWindow(window: com.tmtn.app.network.model.CardWindowResponse) {
        if (setId.value != window.set_id) hasMemoToday.value = null
        setId.value = window.set_id
        cardServiceDate.value = window.service_date
        optionIds.value = window.option_back_ids
        drawState.value = window.draw_state
        todayChallengeId.value = window.challenge_id
        todayChallengeState.value = window.challenge_state
        isTodayRestDay.value = window.is_rest_day
        isTodayGivenUp.value = window.is_given_up
        pickedIndex.value = null
        showConfirmDialog.value = false
        revealedCard.value = revealedCard.value?.takeIf { it.challenge_id == window.challenge_id }?.let {
            it.copy(state = window.challenge_state ?: it.state)
        }
    }

    private fun homeCanRefresh(): Boolean = step.value == CardHomeStep.HOME && !isLoading.value &&
        !showRestDaySheet.value && !showRestCancelSheet.value && !showRestToGiveUpSheet.value &&
        !showGiveUpConfirmSheet.value && !showCheckGiveUpDialog.value && !showConfirmDialog.value

    /** Keep the visible home and its scroll while checking the existing daily-card endpoint. */
    suspend fun refreshHomeOnReturn() {
        if (homeReturnRefreshRunning || !homeCanRefresh()) return
        homeReturnRefreshRunning = true
        val previousSet = setId.value
        try {
            val api = missionApiProvider()
            val response = api.getTodayCards()
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            val window = response.body() ?: error("Empty card window")
            val card = window.challenge_id?.let { id ->
                try {
                    val reveal = api.revealChallenge(id)
                    reveal.body()?.takeIf { reveal.isSuccessful && it.challenge_id == id }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
            // A card, dialog or another day may have opened while the request was in flight.
            if (!homeCanRefresh() || setId.value != previousSet) return
            applyHomeWindow(window)
            if (card != null) revealedCard.value = card
            errorMessage.value = null
            // ⚠️ 2026-09-17 추가(QA 3번) - 미션 완료 후 홈으로 돌아오면 오늘의 카드만
            // 다시 불러오고 댐(재료)·틈튼지수·연속 기록은 완료 전 값 그대로 남아있던
            // 문제 수정. loadHome() 초기 로드와 같은 항목을 병렬로 함께 새로 불러온다.
            coroutineScope {
                launch { loadCompanion() }
                launch { loadTuntunIndexSummary() }
                launch { loadStreak() }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (homeCanRefresh() && setId.value == previousSet) {
                errorMessage.value = "오늘의 소식을 새로 가져오지 못했어요. 다시 돌아오면 확인할게요."
            }
        } finally { homeReturnRefreshRunning = false }
    }

    suspend fun loadStreak() {
        runCatching {
            val response = ApiClient.cardHomeApi.getStreak()
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { streak ->
            restDaysUsedThisWeek.value = streak.rest_days_used_this_week
            restDaysRemainingThisWeek.value = streak.rest_days_remaining_this_week
            currentStreak.value = streak.current_streak
        }
    }

    // ⚠️ 테스트 전용 - 하루 미션 1개 제한 때문에 미션 10개를 이어서 테스트하려면 실제로
    // 10일이 걸림. 서버가 인식하는 "오늘"을 하루 앞당겨서, 오늘 카드를 다시 뽑아 바로
    // 다음 미션으로 이어갈 수 있게 함. 서버(PROD면 404)·클라이언트(디버그 빌드에서만
    // 버튼 노출) 이중으로 막아둬서 실제 서비스에는 영향 없음.
    suspend fun advanceDebugDay() {
        runCatching {
            val response = ApiClient.debugApi.advanceDay()
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { body -> debugSimulatedToday.value = body["simulated_today"]?.toString() }
        loadToday()
    }

    suspend fun resetDebugDay() {
        runCatching {
            val response = ApiClient.debugApi.resetDay()
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { body -> debugSimulatedToday.value = body["simulated_today"]?.toString() }
        loadToday()
    }

    // B01b: "미션 이어하기" - 이미 확정된 오늘 챌린지의 카드 내용을 다시 불러와서 B06으로.
    // ⚠️ 2026-09-04 희주조교님 피드백(P0 ①②) 반영: "미션 이어하기"를 누르면 진행 중이던
    // 타이머/센서 화면으로 바로 가야 하는데, 항상 REVEALED(카드 앞면)로만 보내고 있었음.
    // "이 행동 시작하기"(CardHomeFlow.kt의 onStartAction) 쪽엔 이미 정확한 복구 로직이
    // 있었는데 여긴 재사용을 안 해서 따로 놀고 있었던 것 - 하나로 합쳐서 두 진입점 모두
    // 같은 기준으로 판단하게 함. REVEALED에서 다시 "시작하기"를 누르면 서버가 이미
    // ACTIVE인 챌린지를 또 시작시키려다 409로 튕기는 게 ②(오류)의 원인이었음.
    fun stepForRevealedCard(card: CardRevealResponse): CardHomeStep {
        val step = when {
            // ⚠️ 2026-09-04 반영: COMPLETED/SKIPPED을 별도 완료 화면(CardHomeStep.COMPLETED)
            // 대신 카드 화면(REVEALED)으로 통일 - 완료/쉬어감 정보는 그 화면 하단에 표시됨.
            card.state == "COMPLETED" || card.state == "SKIPPED" -> CardHomeStep.REVEALED
            card.exec_type == "CHECK" -> CardHomeStep.CHALLENGE_CHECK
            card.exec_type == "TIMER" -> when (card.state) {
                "ACTIVE" -> CardHomeStep.CHALLENGE_TIMER_RUNNING
                "PAUSED" -> CardHomeStep.CHALLENGE_TIMER_PAUSED
                else -> CardHomeStep.CHALLENGE_TIMER_START
            }
            else -> {
                // SENSOR형: 프로세스가 살아있는 동안만 추적 중인지 알 수 있음(CurrentChallengeHolder는
                // 메모리 상태라 앱을 완전히 껐다 켜면 리셋됨 - 진짜 복구는 상세 재조회 API가 없어서
                // 아직 미지원, B01b 만들 때 적어둔 것과 같은 제약).
                val alreadyTracking = com.tmtn.app.sensor.CurrentChallengeHolder.challengeId == card.challenge_id &&
                    com.tmtn.app.sensor.CurrentChallengeHolder.execType != null
                if (alreadyTracking) CardHomeStep.SENSOR_MEASURING else CardHomeStep.SENSOR_INTRO
            }
        }
        // ⚠️ 2026-09-04 반영: 타이머 화면(진행 중/일시정지)에 들어갈 때마다 서버가 계산한
        // 실제 경과 시간으로 맞춤. 예전엔 timerElapsedSeconds가 로컬 카운터라, 화면을
        // 벗어났다가(뒤로가기→홈→다시 이어하기 등) 돌아오면 그 사이 실제로 흐른 시간이
        // 반영 안 되고 멈춰있던 것처럼 보였음.
        if (step == CardHomeStep.CHALLENGE_TIMER_RUNNING || step == CardHomeStep.CHALLENGE_TIMER_PAUSED) {
            timerElapsedSeconds.value = card.elapsed_seconds
            timerIsPaused.value = step == CardHomeStep.CHALLENGE_TIMER_PAUSED
        }
        return step
    }

    // ⚠️ 2026-09-04 반영: "미션 이어하기"를 누르면 곧바로 타이머/체크 등 진행 화면으로
    // 들어가서 당황스러웠음 - 먼저 카드 화면(REVEALED)에서 내용을 한번 보여주고, 거기서
    // "진행 중인 미션 확인" 버튼을 눌러야 실제 진행 화면으로 들어가게 함. 완료/중단된
    // 미션은 기존처럼 바로 완료 화면(CardHomeStep.COMPLETED)으로 - 그건 "다시 보기"가
    // 맞는 결과물이라 그대로 둠.
    suspend fun continueTodayMission() {
        val challengeId = todayChallengeId.value ?: return
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = missionApiProvider().revealChallenge(challengeId)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { card ->
            revealedCard.value = card
            // ⚠️ 2026-09-04 재수정: COMPLETED도 SKIPPED와 마찬가지로 "다시 보기"에서는
            // 별도 축하 화면(CompletedScreen) 대신 카드 화면(REVEALED)으로 통일 - 완료/쉬어감
            // 정보는 그 화면 하단에 같이 보여줌(RevealScreen.kt 참고). 방금 막 완료했을 때
            // 뜨는 축하 화면은 goToCompletedOrCelebrate()가 따로 처리하므로 이 함수와는 무관.
            step.value = CardHomeStep.REVEALED
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) { isLoading.value = false; throw e }
            errorMessage.value = e.userMessageOr("미션 정보를 가져오지 못했어요.")
        }
        isLoading.value = false
    }

    // ⚠️ 2026-09-04 반영: REVEALED의 "진행 중인 미션 확인"/"이 행동 시작하기" 전용 - 여기서만
    // stepForRevealedCard()로 실제 진행 상태(타이머/체크/센서 등)에 맞는 화면까지 들어감.
    // continueTodayMission()과 fetch 로직은 같지만 도착 화면 결정 방식이 다름.
    suspend fun refreshAndEnterInProgressMission() {
        val challengeId = revealedCard.value?.challenge_id ?: return
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.cardHomeApi.revealChallenge(challengeId)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { card ->
            revealedCard.value = card
            step.value = stepForRevealedCard(card)
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) { isLoading.value = false; throw e }
            errorMessage.value = e.userMessageOr("미션 정보를 가져오지 못했어요.")
        }
        isLoading.value = false
    }

    // ⚠️ 2026-09-04 반영: REVEALED에 뒤로가기로 들어올 때 캐시된(오래된) 카드 정보를
    // 그대로 보여줘서, 예를 들어 미션 시작 이후 뒤로가기 했는데 "이 행동 시작하기"가
    // 다시 활성화된 것처럼 보이는 등의 문제가 반복적으로 생겼음. startTimer()/pauseTimer()
    // 처럼 동작마다 캐시를 손으로 갱신해두는 방식은 새 동작을 추가할 때마다 빠뜨리기
    // 쉬움 - 그 대신 REVEALED에 들어올 때마다 항상 서버에서 다시 받아오는 쪽으로 통일.
    // step은 안 바꾸고 카드 내용만 새로 고침(stepForRevealedCard()로 다른 화면에 보내지
    // 않음) - "여기서는 카드 화면 자체를 보여주는 게 목적"이라 refreshAndEnterInProgressMission()과는
    // 다름.
    suspend fun refreshRevealedCard() {
        val challengeId = revealedCard.value?.challenge_id ?: return
        runCatching {
            val response = missionApiProvider().revealChallenge(challengeId)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { card ->
            revealedCard.value = card
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) { isLoading.value = false; throw e }
            errorMessage.value = e.userMessageOr("미션 정보를 가져오지 못했어요.")
        }
    }

    // ⚠️ 2026-09-06 QA(P0-1) 반영: 타이머가 Compose 화면이 떠 있는 동안에만 로컬로
    // 초 단위 카운트를 세고 있어서, 앱이 백그라운드로 가면(다른 앱 전환, 화면 끔 등)
    // 그 사이 실제로 흐른 시간이 전혀 반영이 안 됨 - 심하면 Activity가 재생성되면서
    // 카운트가 훨씬 작은 값으로 "되감기"는 것처럼 보이기도 함. 근본적으로는 SENSOR처럼
    // 포그라운드 서비스로 옮겨야 하지만(더 큰 작업), 그 전까지는 화면이 다시 보일
    // 때마다(Activity onResume) 서버가 계산한 실제 경과 시간(elapsed_seconds, 서버가
    // started_at 기준으로 정확히 계산해줌)으로 무조건 재동기화해서 "다시 열면 이어서
    // 진행합니다"라는 화면 문구가 실제로 사실이 되게 함.
    suspend fun syncTimerElapsedFromServer() {
        val challengeId = revealedCard.value?.challenge_id ?: return
        runCatching {
            val response = ApiClient.cardHomeApi.revealChallenge(challengeId)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { card ->
            revealedCard.value = card
            timerElapsedSeconds.value = card.elapsed_seconds
            timerIsPaused.value = card.state == "PAUSED"
        }
        // 실패하면 조용히 무시 - 로컬 값 그대로 유지(백그라운드 복귀 시점의 부가 동기화라
        // 실패했다고 화면에 에러를 띄우면 오히려 방해됨. 다음 기회에 다시 시도됨).
    }

    // ⚠️ 2026-09-08 QA(9번) 반영: LOCATION_DATA_USAGE(위치정보, 선택 동의) 여부를 조회해서
    // SensorIntroScreen이 센서 미션 시작 자체를 막을 수 있게 함.
    suspend fun loadLocationConsent() {
        locationConsentLoaded.value = false
        locationConsentGranted.value = false
        runCatching {
            val response = ApiClient.profileApi.listConsents()
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { consents ->
            locationConsentLoaded.value = true
            locationConsentGranted.value = consents.any {
                it.purpose == "LOCATION_DATA_USAGE" && it.status == "AGREED"
            }
        }
    }

    suspend fun loadCompanion() {
        runCatching {
            val response = ApiClient.cardHomeApi.getCompanionStatus()
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { body ->
            companionStage.value = body.current_stage
            companionLoaded.value = true
            companionMaterialsNeeded.value = body.materials_needed_for_next
            companionNextStageLabel.value = body.stages.firstOrNull { !it.completed }?.label
            companionMaterials.value = body.materials
        }
    }

    // ⚠️ PR #12 리뷰(P1) 반영: 예전엔 실패해도 그냥 아무 일 없이 넘어가서, 이미 다 입력한
    // 사용자가 오프라인이면 "아직 계산할 수 없어요 · 입력해 보세요"라고 잘못 안내됐음.
    // 네트워크 실패와 "진짜로 입력이 없어서 계산 불가"를 구분하기 위한 플래그.
    var tuntunIndexLoadFailed = mutableStateOf(false)

    suspend fun loadTuntunIndexSummary() {
        tuntunIndexLoadFailed.value = false
        if (!peerScoreEnabled) {
            tuntunIndexPresentationValue.value = null
            tuntunIndexValue.value = null
            return
        }
        val body = runCatching {
            val response = ApiClient.tuntunScoreApi.getTuntunScorePeerV2()
            if (response.isSuccessful) response.body() else null
        }.getOrNull()
        if (body == null) {
            // ⚠️ 2026-09-09 반영: 브릿지 미설정/미가동(503) 등도 이 분기로 옴 - "네트워크
            // 실패"와 동일하게 취급. Mock 고정값을 조용히 대신 보여주지 않음.
            tuntunIndexLoadFailed.value = true
            return
        }
        val index = body.peerCompositeScore
        if (body.scoreAvailable && !body.isMock && index != null && index.isFinite() && index in 0.0..100.0) {
            tuntunIndexPresentationValue.value = index
            tuntunIndexValue.value = index.roundToInt()
            tuntunIndexIsMock.value = body.isMock
        } else {
            tuntunIndexPresentationValue.value = null
            tuntunIndexValue.value = null
        }
    }

    // ⚠️ PR #12 리뷰(P1) 반영: 실패하면 recentWeek가 빈 리스트로 남아서 "이번 주 0일
    // 실천했어요"가 뜸 - 5일 실천한 사람에게 0일이라고 잘못 말하게 됨. 실패했을 때는
    // 이전 값을 그대로 유지하고, 실패 여부만 별도로 표시함.
    var recentWeekLoadFailed = mutableStateOf(false)

    suspend fun loadRecentWeek() {
        val body = runCatching {
            val response = ApiClient.recordApi.getWeeklyReport()
            if (response.isSuccessful) response.body() else null
        }.getOrNull()
        if (body == null) {
            recentWeekLoadFailed.value = true
            return
        }
        recentWeekLoadFailed.value = false
        recentWeek.value = body.days
    }

    // B03 -> B04: 카드 한 장을 "가운데로" 고름 (아직 서버에 확정 안 함)
    // ⚠️ 2026-09-06 반영: 카드를 하나 고른 뒤에 다른 카드를 눌러도 선택이 그냥 바뀌던
    // 버그(혁수님 할일 문서 2-1과 같은 지적) - 한 장을 고르면 "다시 고르기"를 눌러
    // pickedIndex를 비우기 전까지는 다른 카드를 눌러도 무시되게 함.
    fun pickCard(index: Int) {
        if (pickedIndex.value != null) return
        pickedIndex.value = index
    }

    fun resetPick() {
        pickedIndex.value = null
        showConfirmDialog.value = false
        errorMessage.value = null
    }

    // B04 -> B05: 확정 다이얼로그 열기
    fun openConfirmDialog() {
        errorMessage.value = null
        showConfirmDialog.value = true
    }

    // B05: "확정하기" -> 실제 서버 호출
    suspend fun confirmCard() {
        if (isLoading.value) return
        val sid = setId.value ?: return
        val index = pickedIndex.value ?: return
        val optionId = optionIds.value.getOrNull(index) ?: return

        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val api = missionApiProvider()
            val response = api.selectCard(sid, optionId)
            if (response.code() == 409) {
                // A previous confirmation may have succeeded while its reply was lost.
                val windowResponse = api.getTodayCards()
                val window = windowResponse.body()?.takeIf { windowResponse.isSuccessful && it.set_id == sid && it.draw_state == "SELECTED" }
                val existingId = window?.challenge_id
                    ?: failWithMessage("고른 카드를 확인하지 못했어요. 홈에서 다시 확인해 주세요.")
                val existing = api.revealChallenge(existingId)
                existing.body()?.takeIf { existing.isSuccessful && it.challenge_id == existingId }
                    ?: failWithMessage("고른 카드를 가져오지 못했어요. 다시 눌러 주세요.")
            } else {
                if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
                response.body() ?: failWithMessage("카드를 가져오지 못했어요. 다시 눌러 주세요.")
            }
        }.onSuccess { reveal ->
            com.tmtn.app.audio.TmtnAudio.play(com.tmtn.app.audio.TmtnSound.Paper, "${reveal.challenge_id}:reveal")
            revealedCard.value = reveal
            todayChallengeId.value = reveal.challenge_id
            todayChallengeState.value = reveal.state
            drawState.value = "SELECTED"
            showConfirmDialog.value = false
            step.value = CardHomeStep.REVEALED
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) { isLoading.value = false; throw e }
            errorMessage.value = e.userMessageOr("카드 확정에 실패했어요.")
            showConfirmDialog.value = true
        }
        isLoading.value = false
    }

    // B06: "이 행동 시작하기" 중 CHECK형(자가 확인)은 바로 완료 처리
    suspend fun completeChallenge() {
        val challengeId = revealedCard.value?.challenge_id ?: return
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.cardHomeApi.completeChallenge(
                challengeId, UUID.randomUUID().toString(), CompleteChallengeRequestBody()
            )
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { result ->
            pointsAwarded.value = result.points_awarded
            com.tmtn.app.audio.TmtnAudio.play(com.tmtn.app.audio.TmtnSound.Reward, "$challengeId:complete")
            awardedFiveElement.value = result.five_element
            goToCompletedOrCelebrate()
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) { isLoading.value = false; throw e }
            errorMessage.value = e.userMessageOr("완료 처리에 실패했어요.")
        }
        isLoading.value = false
    }

    // B16: 바텀시트 열 때 - 현재 이번 주 쉼 사용 현황을 먼저 불러옴
    suspend fun openRestDaySheet() {
        // ⚠️ RevealScreen에서 버튼 자체는 숨겼지만, CheckChallengeScreen/홈 화면 등 다른
        // 진입점에서도 이 함수를 공유해서 부르므로 여기서도 한 번 더 막아둠. 이미 완료된
        // 미션인데 "쉬어가기"까지 확정되면 이번 주 쉼 횟수만 잘못 깎여나감(완료+쉼이 동시에
        // 찍히는 모순).
        //
        // ⚠️ 2026-09-06 반영: revealedCard.value?.state도 같이 확인했었는데, 이 값은
        // loadToday()가 새로고침해도 안 지워지는 값이라 어제(또는 이전 날) 완료했던 카드
        // 정보가 그대로 남아있음. "테스트용 다음 날로"로 날짜를 넘긴 뒤 "오늘은 쉬어가기"를
        // 누르면, 새 날짜인데도 어제 완료 기록 때문에 여기서 조용히 막혀버렸음. 항상 최신으로
        // 갱신되는 todayChallengeState만 신뢰하도록 정리.
        //
        // ⚠️ 2026-09-08 QA 반영: SKIPPED(포기)도 COMPLETED와 똑같이 막고 있어서, 상태전이
        // 정책이 명시적으로 지원하는 "포기 -> 쉬어가기 전환"(GIVE_UP -> REST, 서버
        // record_service.mark_rest_day도 이미 challenge.state==SKIPPED는 허용하도록
        // 만들어져 있음)이 눌러도 반응 없는 버튼이 돼 있었음. COMPLETED만 막음.
        val finished = todayChallengeState.value == "COMPLETED"
        if (finished) return

        loadStreak()
        showRestDaySheet.value = true
    }

    // B16 -> B17: "오늘 쉬어가기" 확정 - 실제 서버 호출 (기록 캘린더 API 재사용)
    suspend fun confirmRestDay(): Boolean = transitionMission("쉼으로 저장하지 못했어요. 다시 눌러 주세요.") {
        val today = serviceDateProvider()
            val response = missionApiProvider().markRestDay(RestDayRequest(service_date = today))
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            val streak = response.body() ?: failWithMessage("쉼으로 저장하지 못했어요. 다시 눌러 주세요.")
            restDaysUsedThisWeek.value = streak.rest_days_used_this_week
            restDaysRemainingThisWeek.value = streak.rest_days_remaining_this_week
            currentStreak.value = streak.current_streak
            isTodayRestDay.value = true
            showRestDaySheet.value = false
            // ⚠️ 예전엔 여기서 REST_DAY_DONE(별도 화면)으로 보냈는데, 그 화면엔 마스코트
            // 카드만 있고 틈튼지수 요약·최근7일·댐 정보가 다 빠져있어서 "홈이 통째로 사라진
            // 것처럼" 보인다는 피드백을 받음. 이제 홈으로 바로 보내고, 홈 안의 마스코트
            // 카드만 "쉼" 버전으로 바뀌게 함(CardHomeScreen.kt 참고).
            step.value = CardHomeStep.HOME
    }

    fun closeRestDaySheet() {
        if (isLoading.value) return
        // ⚠️ 예전엔 여기서도 step.value = CardHomeStep.HOME으로 보내서, RevealScreen이나
        // CheckChallengeScreen에서 열었어도 "닫기"만 누르면 무조건 홈으로 튕겼음. 이제
        // 오버레이라 그냥 닫기만 하면 원래 보고 있던 화면이 자연스럽게 그대로 보임.
        showRestDaySheet.value = false
        errorMessage.value = null
    }

    // ===== 2026-09-07 반영: 상태전이 정책 REST<->GIVE_UP 전환·재시작 =====

    // C25 · "쉬어가기 취소하고 도전하기" - REST -> 도전 복귀(+1회 복구). 시트 열림 자체는
    // showRestCancelSheet로 관리하고, 화면 전환(DECK_PICK vs 진행 화면)은 drawState를 보고
    // 호출부(CardHomeFlow.kt)가 결정함 - 여기서는 서버 호출 + 잔여횟수·상태만 갱신.
    suspend fun cancelRestDay(): Boolean = transitionMission("쉬어가기를 취소하지 못했어요. 다시 눌러 주세요.") {
        val today = serviceDateProvider()
            val response = missionApiProvider().cancelRestDay(today)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            val streak = response.body() ?: failWithMessage("쉬어가기를 취소하지 못했어요. 다시 눌러 주세요.")
            restDaysUsedThisWeek.value = streak.rest_days_used_this_week
            restDaysRemainingThisWeek.value = streak.rest_days_remaining_this_week
            currentStreak.value = streak.current_streak
            isTodayRestDay.value = false
    }

    // C27 · "포기로 바꾸기" - REST -> GIVE_UP(쓴 쉬어가기 1회 복구, 대신 연속 기록은 끊김).
    // switchToGiveUp()이 "쉬어가기 취소 + 포기 기록"을 서버에서 하나의 트랜잭션으로 처리함
    // (record_service.py 주석 참고) - 클라이언트에서 cancelRestDay + quitChallenge를 따로
    // 두 번 부르지 않음(중간 실패 시 상태 불일치 방지).
    suspend fun switchRestToGiveUp(): Boolean = transitionMission("오늘 기록을 바꾸지 못했어요. 다시 눌러 주세요.") {
        val today = serviceDateProvider()
            val response = missionApiProvider().switchToGiveUp(RestDayRequest(service_date = today))
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            val streak = response.body() ?: failWithMessage("오늘 기록을 바꾸지 못했어요. 다시 눌러 주세요.")
            restDaysUsedThisWeek.value = streak.rest_days_used_this_week
            restDaysRemainingThisWeek.value = streak.rest_days_remaining_this_week
            currentStreak.value = streak.current_streak
            isTodayRestDay.value = false
            isTodayGivenUp.value = true
            todayChallengeState.value = todayChallengeId.value?.let { "SKIPPED" }
            revealedCard.value = revealedCard.value?.copy(state = "SKIPPED", elapsed_seconds = 0, accumulated_count = 0)
            timerElapsedSeconds.value = 0
            timerIsPaused.value = true
            step.value = CardHomeStep.HOME
    }

    // RevealScreen의 "다시 도전하기"(SKIPPED에서 재도전) - G2로 서버가 SKIPPED에서도
    // 재시작을 허용하니, startChallenge()만 다시 부르면 됨. exec_type이 TIMER/CHECK/SENSOR
    // 중 무엇이든 정확한 화면으로 보내야 해서, 성공 후 revealChallenge()로 최신 카드를
    // 다시 받아와 stepForRevealedCard()로 이동함(startTimer()처럼 무조건 한 화면으로
    // 고정하면 CHECK·SENSOR형이 잘못된 화면으로 감).
    suspend fun restartFromGiveUp() {
        val challengeId = revealedCard.value?.challenge_id ?: return
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.cardHomeApi.startChallenge(challengeId)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            val revealResponse = ApiClient.cardHomeApi.revealChallenge(challengeId)
            if (!revealResponse.isSuccessful) failWithMessage(parseErrorMessage(revealResponse))
            revealResponse.body()!!
        }.onSuccess { card ->
            revealedCard.value = card
            isTodayGivenUp.value = false
            step.value = stepForRevealedCard(card)
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) { isLoading.value = false; throw e }
            errorMessage.value = e.userMessageOr("다시 시작하지 못했어요.")
        }
        isLoading.value = false
    }

    // B22(중단)의 "미션 이어서 하기" · C25의 "쉬어가기 취소하고 도전하기"(이미 카드를
    // 확정한 날) 전용 - 홈 화면에서 직접 호출되므로 revealedCard가 아직 비어있을 수 있어
    // todayChallengeId를 기준으로 씀(continueTodayMission()과 fetch는 같지만, REVEALED가
    // 아니라 stepForRevealedCard()로 곧장 진행 화면까지 들어가는 게 다름 -
    // refreshAndEnterInProgressMission()과 같은 목적, 소스만 다름).
    suspend fun enterInProgressMission() {
        val challengeId = todayChallengeId.value ?: return
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.cardHomeApi.revealChallenge(challengeId)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { card ->
            revealedCard.value = card
            step.value = stepForRevealedCard(card)
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) { isLoading.value = false; throw e }
            errorMessage.value = e.userMessageOr("미션 정보를 가져오지 못했어요.")
        }
        isLoading.value = false
    }

    // ===== C그룹: 챌린지 진행 (타이머형) =====

    // Server acknowledgement comes before the local timer transition. A failed request
    // keeps the current clock and state so the same action can be retried safely.
    private suspend fun transitionMission(fallback: String, action: suspend () -> Unit): Boolean {
        if (isLoading.value) return false
        isLoading.value = true
        errorMessage.value = null
        return try { action(); true }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { errorMessage.value = error.userMessageOr(fallback); false }
        finally { isLoading.value = false }
    }

    suspend fun startTimer() {
        val card = revealedCard.value ?: return
        transitionMission("시작하지 못했어요. 연결을 확인하고 다시 눌러 주세요.") {
            val response = missionApiProvider().startChallenge(card.challenge_id)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            timerElapsedSeconds.value = response.body()?.accumulated_duration_seconds ?: card.elapsed_seconds
            timerIsPaused.value = false
            revealedCard.value = card.copy(state = "ACTIVE")
            step.value = CardHomeStep.CHALLENGE_TIMER_RUNNING
        }
    }

    suspend fun pauseTimer() {
        val card = revealedCard.value ?: return
        transitionMission("잠시 멈추지 못했어요. 연결을 확인하고 다시 눌러 주세요.") {
            val response = missionApiProvider().pauseChallenge(card.challenge_id)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            response.body()?.let { timerElapsedSeconds.value = it.accumulated_duration_seconds }
            timerIsPaused.value = true
            revealedCard.value = card.copy(state = "PAUSED", elapsed_seconds = timerElapsedSeconds.value)
            step.value = CardHomeStep.CHALLENGE_TIMER_PAUSED
        }
    }

    suspend fun resumeTimer() {
        val card = revealedCard.value ?: return
        transitionMission("다시 시작하지 못했어요. 잰 시간은 그대로예요.") {
            val response = missionApiProvider().startChallenge(card.challenge_id)
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            response.body()?.let { timerElapsedSeconds.value = it.accumulated_duration_seconds }
            revealedCard.value = card.copy(state = "ACTIVE")
            timerIsPaused.value = false
            step.value = CardHomeStep.CHALLENGE_TIMER_RUNNING
        }
    }

    suspend fun quitChallenge(onSaved: () -> Unit = {}): Boolean {
        val card = revealedCard.value ?: return false
        val saved = transitionMission("중단하지 못했어요. 미션은 그대로 열려 있어요.") {
            val response = missionApiProvider().skipChallenge(card.challenge_id,
                com.tmtn.app.network.model.SkipChallengeRequest(reason = "사용자가 중단함"))
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            revealedCard.value = card.copy(state = "SKIPPED")
            timerIsPaused.value = true
            todayChallengeState.value = "SKIPPED"
        }
        if (saved) {
            showGiveUpConfirmSheet.value = false
            showQuitDialog.value = false
            showCheckGiveUpDialog.value = false
            onSaved()
            loadToday()
        }
        return saved
    }

    suspend fun pauseAndGoHome() {
        val card = revealedCard.value ?: return
        val saved = transitionMission("저장하지 못했어요. 이 화면에서 다시 시도할 수 있어요.") {
            // An unstarted check mission and an already paused mission need no transition.
            if (card.state == "ACTIVE") {
                val response = missionApiProvider().pauseChallenge(card.challenge_id)
                if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
                response.body()?.let { timerElapsedSeconds.value = it.accumulated_duration_seconds }
                revealedCard.value = card.copy(state = "PAUSED", elapsed_seconds = timerElapsedSeconds.value)
                todayChallengeState.value = "PAUSED"
            }
            timerIsPaused.value = true
            showQuitDialog.value = false
            showCheckGiveUpDialog.value = false
            step.value = CardHomeStep.HOME
        }
        if (saved) loadToday()
    }

    // C03/C04 -> C05 -> (성공하면) C20: 목표를 채운 뒤 완료 처리
    suspend fun completeTimerChallenge(manualCheck: Boolean = false) {
        if (isLoading.value) return
        val challengeId = revealedCard.value?.challenge_id ?: return
        step.value = CardHomeStep.CHALLENGE_PROCESSING
        val attempt = "$challengeId:$manualCheck"
        if (completionAttempt?.first != attempt) completionAttempt = attempt to UUID.randomUUID().toString()
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = missionApiProvider().completeChallenge(
                challengeId, completionAttempt!!.second, CompleteChallengeRequestBody(manual_check = manualCheck)
            )
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { result ->
            pointsAwarded.value = result.points_awarded
            awardedFiveElement.value = result.five_element
            step.value = CardHomeStep.CHALLENGE_RETROSPECT
            com.tmtn.app.audio.TmtnAudio.play(com.tmtn.app.audio.TmtnSound.Reward, "$challengeId:complete")
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) {
                isLoading.value = false
                throw e
            }
            errorMessage.value = e.userMessageOr("완료 처리에 실패했어요.")
            // ⚠️ 여기서 무조건 CHALLENGE_TIMER_RUNNING으로 보내던 게 버그였음. CHECK형은
            // 원래 타이머 화면에 들어간 적도 없는데 실패하면 갑자기 타이머가 도는 화면으로
            // 튕겨서, "완료하기 눌렀는데 왜 타이머가 시작되냐"는 혼란 + 그 화면에서 다시
            // 시도해도 계속 실패하는 문제로 이어졌음. exec_type에 맞는 원래 화면으로 되돌림.
            //
            // ⚠️ 2026-09-04 추가 반영: SENSOR형(걷기/뛰기/계단)도 여기로 빠지면 무조건
            // CHALLENGE_TIMER_RUNNING으로 갔었음 - 거리(m) 목표를 *60 해서 완전히 엉뚱한
            // "시간" 목표(예: 300m -> 18000초=5시간)로 된 타이머가 도는 화면이 뜨는 버그였음.
            // exec_type이 SENSOR_*면 SENSOR_MEASURING으로 돌려보냄.
            step.value = when {
                manualCheck -> CardHomeStep.CHALLENGE_CHECK_CONFIRM
                revealedCard.value?.exec_type == "CHECK" -> CardHomeStep.CHALLENGE_CHECK_CONFIRM
                revealedCard.value?.exec_type?.startsWith("SENSOR_") == true -> CardHomeStep.SENSOR_MEASURING
                else -> CardHomeStep.CHALLENGE_TIMER_RUNNING
            }
        }
        isLoading.value = false
    }

    // C20: 한 줄 회고 저장 (기록 캘린더의 메모 API 재사용) - 저장/건너뛰기 둘 다 결국 완료 화면으로
    suspend fun submitRetrospect(memo: String?) {
        if (isLoading.value) return
        errorMessage.value = null
        if (memo.isNullOrBlank()) {
            goToCompletedOrCelebrate()
            return
        }
        isLoading.value = true
        try {
            val response = missionApiProvider().updateDayMemo(
                currentServiceDateString(), MemoUpdateRequest(memo = memo.trim().take(100)),
            )
            if (!response.isSuccessful) failWithMessage(parseErrorMessage(response))
            hasMemoToday.value = true
            goToCompletedOrCelebrate()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage.value = "메모를 저장하지 못했어요. 입력한 내용은 그대로예요. 다시 저장해 주세요."
        } finally {
            isLoading.value = false
        }
    }

    // B07: "한 줄 남기기" 버튼을 보여줄지 판단 - 오늘 이미 회고를 남겼으면 버튼 자체를 숨김.
    suspend fun checkTodayMemo() {
        val today = currentServiceDateString() // ⚠️ 서버 날짜 기준(테스트 시뮬레이션 반영)
        runCatching {
            val response = ApiClient.recordApi.getDayDetail(today)
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { detail ->
            hasMemoToday.value = !detail.memo.isNullOrBlank()
        }
    }

    // ⚠️ 2026-09-11 추가 - 틈새 운동(TMtn_UI_V17 §5). "오늘의 카드" 완료 후에만 실제로
    // 시작 가능하지만, 목록 자체는 항상 조회 가능(card_completed=false면 화면에서
    // "카드부터 완료해 주세요" 안내로 대체 - B17 카드 완료 화면 안내와 일관되게).
    var exerciseMissionsToday = mutableStateOf<ExerciseMissionsTodayResponse?>(null)
    var selectedExerciseOption = mutableStateOf<ExerciseMissionOption?>(null)
    var activeExerciseSession = mutableStateOf<ExerciseMissionSessionResponse?>(null)

    // ⚠️ 2026-09-17 추가(QA F13, 홍주님 회신) - "저장 중/저장 실패/저장 완료"를 구분해서
    // 화면이 참고할 수 있게 함. 저장 완료는 별도 값 없이 exerciseRewardResult가 채워지고
    // step이 EXTRA_REWARD로 넘어가는 것 자체로 표현한다(서버가 확인해준 결과만 완료로 침).
    var exerciseSaveState = mutableStateOf(ExerciseSaveState.IDLE)
    var exerciseRewardResult = mutableStateOf<CompleteExerciseMissionSessionResponse?>(null)

    suspend fun loadExerciseMissionsToday() {
        runCatching {
            val response = ApiClient.exerciseMissionApi.getToday()
            if (response.isSuccessful) response.body() else null
        }.onSuccess { body ->
            exerciseMissionsToday.value = body
            // ⚠️ 2026-09-16 추가(QA) - 진행 중(ACTIVE/PAUSED)인 세션이 있으면 목록 대신
            // 바로 진행 화면으로 이어줌. 오늘의 카드 Challenge의 "미션 이어하기"와 같은
            // 원칙 - 뒤로가기·홈 버튼으로 화면을 나가도 세션을 취소하지 않으니(더 이상
            // 자동 취소 안 함), 다음에 들어올 때 여기서 이어서 하게 해줘야 함.
            val active = body?.active_session
            if (active != null) {
                activeExerciseSession.value = active
                step.value = CardHomeStep.EXTRA_RUNNING
            }
        }
    }

    fun openExerciseMissionList() {
        step.value = CardHomeStep.EXTRA_LIST
    }

    fun selectExerciseOption(option: ExerciseMissionOption) {
        selectedExerciseOption.value = option
        step.value = CardHomeStep.EXTRA_DETAIL
    }

    // ⚠️ B32/B43 "이 운동 시작하기/실천하기" 버튼. 서버가 세션을 만들면서 바로 ACTIVE로
    // 시작함(Challenge의 start()와 달리 create가 곧 시작 - 틈새 운동은 "고르는 순간
    // 바로 시작"이 자연스러운 흐름이라 별도 시작 API를 안 둠).
    suspend fun startExerciseMission(): Boolean {
        val option = selectedExerciseOption.value ?: return false
        val idempotencyKey = UUID.randomUUID().toString()
        return runCatching {
            val response = ApiClient.exerciseMissionApi.createSession(
                CreateExerciseMissionSessionRequest(catalog_entry_id = option.catalog_entry_id, idempotency_key = idempotencyKey)
            )
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { session ->
            activeExerciseSession.value = session
            // CHECK형은 측정 화면 없이 바로 완료 확인으로 넘어감(문서: "직접 완료를 확인해요")
            step.value = CardHomeStep.EXTRA_RUNNING
            true
        } ?: false
    }

    // ⚠️ 2026-09-16 - accumulatedDurationSeconds 파라미터 추가(QA F05, 같은 이유).
    // ⚠️ 원인분석 문서 F02 - 이 함수(그리고 resumeExerciseMission)는 정의는 있지만
    // 실제 화면에서 호출하는 곳이 아직 없음(별도 확인 필요 - 이번엔 시그니처만 준비).
    suspend fun pauseExerciseMission(accumulatedDurationSeconds: Int? = null) {
        val session = activeExerciseSession.value ?: return
        runCatching {
            ApiClient.exerciseMissionApi.patchSession(
                session.id,
                ExerciseMissionActionRequest(
                    action = "pause", accumulated_duration_seconds = accumulatedDurationSeconds,
                ),
            )
        }.onSuccess { response -> if (response.isSuccessful) activeExerciseSession.value = response.body() }
    }

    suspend fun resumeExerciseMission() {
        val session = activeExerciseSession.value ?: return
        runCatching {
            ApiClient.exerciseMissionApi.patchSession(session.id, ExerciseMissionActionRequest(action = "resume"))
        }.onSuccess { response -> if (response.isSuccessful) activeExerciseSession.value = response.body() }
    }

    // ⚠️ 2026-09-16 추가(QA F05) - accumulatedDurationSeconds 파라미터 신규. 시간형
    // (걷기/달리기) 완료 시 화면이 실제로 표시 중이던 확정 시간(liveWalkingSeconds 등)을
    // 실어 보내야, 서버가 더 이상 "경과 시각"으로 대신 계산 안 하고 이 값을 그대로 씀.
    suspend fun completeExerciseMission(
        manualCheck: Boolean = false, accumulatedCount: Int? = null, accumulatedDurationSeconds: Int? = null,
    ) {
        val session = activeExerciseSession.value ?: return
        // ⚠️ 2026-09-16 버그 수정(QA F09) - 예전엔 매번 새 UUID.randomUUID()를 써서,
        // 완료가 서버에서는 성공했는데 응답만 못 받고 실패로 보여서 사용자가 다시
        // 누르면, 서버 입장에선 "새 요청"이라 이미 COMPLETED인 세션을 다시 트랜지션
        // 못 해서 거절함(멱등키를 쓴 의미가 없었음) - 세션 ID 기반 결정론적 키로
        // 바꿔서, 같은 세션에 대한 재시도는 항상 같은 키가 나오게 함(서버가 그 키로
        // 기존 결과를 그대로 복구해서 돌려줌).
        val idempotencyKey = UUID.nameUUIDFromBytes("exercise-complete:${session.id}".toByteArray()).toString()
        // ⚠️ 2026-09-17 추가(QA F13/F14) - "저장 중" 표시 + 원인 확인용 로그. 같은
        // idempotencyKey로 재시도해도(F09에서 이미 결정론적 키로 바꿔둠) 중복 지급은
        // 안 되고, 서버가 이미 처리했으면 그 결과를 그대로 돌려받는다.
        exerciseSaveState.value = ExerciseSaveState.SAVING
        errorMessage.value = null
        android.util.Log.i(
            "ExerciseMissionSave",
            "complete start: sessionId=${session.id} manualCheck=$manualCheck " +
                "accumulatedCount=$accumulatedCount accumulatedDurationSeconds=$accumulatedDurationSeconds " +
                "appVersion=${com.tmtn.app.BuildConfig.VERSION_NAME}",
        )
        runCatching {
            ApiClient.exerciseMissionApi.completeSession(
                session.id,
                CompleteExerciseMissionSessionRequest(
                    idempotency_key = idempotencyKey, manual_check = manualCheck,
                    accumulated_count = accumulatedCount,
                    accumulated_duration_seconds = accumulatedDurationSeconds,
                ),
            )
        }.onSuccess { response ->
            if (response.isSuccessful) {
                android.util.Log.i("ExerciseMissionSave", "complete success: sessionId=${session.id} rewardSlot=${response.body()?.reward_slot}")
                exerciseSaveState.value = ExerciseSaveState.IDLE
                exerciseRewardResult.value = response.body()
                step.value = CardHomeStep.EXTRA_REWARD
            } else {
                // ⚠️ 목표 미달성(409)은 "저장 실패"가 아니라 정상적인 검증 결과라
                // FAILED로 두지 않는다 - "다시 저장하기"가 아니라 운동을 더 해야 함.
                android.util.Log.w("ExerciseMissionSave", "complete rejected: sessionId=${session.id} httpCode=${response.code()}")
                exerciseSaveState.value = ExerciseSaveState.IDLE
                errorMessage.value = "아직 목표에 도달하지 못했어요."
            }
        }.onFailure { e ->
            // ⚠️ 2026-09-16 추가(QA F09) - 예전엔 네트워크 예외(타임아웃 등) 시 아무
            // 처리가 없어서 "완료 버튼을 눌러도 반응 없음"처럼 보였음. 취소된 코루틴은
            // 그대로 전파해야 함(정상적인 화면 이탈 취소).
            if (e is kotlinx.coroutines.CancellationException) throw e
            // ⚠️ 2026-09-17 수정(QA F13, 홍주님 지정 문구) - "저장 실패"를 화면에 명확히
            // 남기고, 운동 기록(activeExerciseSession)은 건드리지 않아 재시도해도 유지되게 함.
            android.util.Log.e("ExerciseMissionSave", "complete failed: sessionId=${session.id} error=${e::class.simpleName}: ${e.message}")
            exerciseSaveState.value = ExerciseSaveState.FAILED
            errorMessage.value = "운동은 마쳤어요. 기록 저장을 다시 시도해주세요."
        }
    }

    // ⚠️ 2026-09-16 버그 수정(QA F09) - 예전엔 API 성공/실패와 무관하게 무조건 로컬
    // 세션을 null로 지우고 COMPLETED로 넘어갔음 - 취소가 실제로 실패하면 서버엔 그
    // 세션이 여전히 ACTIVE로 남아있는데, 앱에서는 사라져서 다음에 같은 운동을 다시
    // 시작하려 하면 "이미 진행 중"으로 막히는 고립 상태가 됨(F02와 같은 근본 원인).
    // 이제 성공했을 때만 로컬 상태를 정리하고, 실패하면 에러를 보여주고 세션을 그대로
    // 유지함 - 다음 홈 진입 때(F06 수정) 서버 상태로 다시 맞춰지거나, 사용자가 다시
    // 시도할 수 있음.
    suspend fun cancelExerciseMission() {
        val session = activeExerciseSession.value ?: return
        runCatching { ApiClient.exerciseMissionApi.cancelSession(session.id) }
            .onSuccess { response ->
                if (response.isSuccessful) {
                    activeExerciseSession.value = null
                    step.value = CardHomeStep.COMPLETED
                } else {
                    errorMessage.value = "그만두기를 처리하지 못했어요. 다시 시도해 주세요."
                }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorMessage.value = "연결이 원활하지 않아요. 다시 시도해 주세요."
            }
    }
}
