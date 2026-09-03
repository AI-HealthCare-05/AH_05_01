package com.tmtn.app.ui.cardhome

import androidx.compose.runtime.mutableStateOf
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.network.model.CompleteChallengeRequestBody
import com.tmtn.app.network.model.MaterialItem
import com.tmtn.app.network.model.MemoUpdateRequest
import com.tmtn.app.network.model.RestDayRequest
import com.tmtn.app.ui.onboarding.parseErrorMessage
import java.util.UUID

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
}

/**
 * 오늘의 카드 흐름 전체(B01~B09) 상태.
 * Navigation Compose 없이 온보딩과 동일한 방식(상태값으로 화면 전환)으로 구현.
 */
class CardHomeState {
    var step = mutableStateOf(CardHomeStep.LOADING)
    var isLoading = mutableStateOf(false)
    var errorMessage = mutableStateOf<String?>(null)

    // 오늘의 카드 세트
    var setId = mutableStateOf<String?>(null)
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

    // 댐(재료) 요약 - B01/B01b 상단에 표시
    var companionStage = mutableStateOf(0)
    var companionMaterialsNeeded = mutableStateOf<Int?>(null)
    var companionNextStageLabel = mutableStateOf<String?>(null)
    // 홈 화면 "댐" 요약 행의 오른쪽 빈 공간에 재료 개수를 보여주기 위한 값 (RecentSummaryListCard 참고).
    var companionMaterials = mutableStateOf<List<MaterialItem>>(emptyList())

    // B16/B17: 쉬어가기
    var restDaysUsedThisWeek = mutableStateOf(0)
    var restDaysRemainingThisWeek = mutableStateOf(2)
    var currentStreak = mutableStateOf(0)
    var isTodayRestDay = mutableStateOf(false)
    // B16: "오늘 쉬어가기" 시트를 지금 화면 위에 띄울지 - 더 이상 별도 step이 아니라
    // D그룹 DayDetailSheet와 같은 오버레이 방식(RestDaySheetScreen 오버레이 참고).
    var showRestDaySheet = mutableStateOf(false)

    // C그룹: 챌린지 진행 (타이머형)
    var timerElapsedSeconds = mutableStateOf(0)
    var timerIsPaused = mutableStateOf(false)
    var showQuitDialog = mutableStateOf(false)

    // G07: 완료 직후 단계 상승 여부
    var stageUpPending = mutableStateOf<com.tmtn.app.network.model.StageUpPendingResponse?>(null)

    /** 완료 관련 함수들이 직접 COMPLETED로 안 가고 항상 이걸 거침 - 단계가 올랐으면
     * G07(STAGE_UP)을 먼저 보여주고, 아니면 그대로 COMPLETED로. */
    private suspend fun goToCompletedOrCelebrate() {
        val pending = runCatching { ApiClient.cardHomeApi.getStageUpPending() }
            .getOrNull()?.let { if (it.isSuccessful) it.body() else null }
        if (pending != null) {
            stageUpPending.value = pending
            step.value = CardHomeStep.STAGE_UP
        } else {
            step.value = CardHomeStep.COMPLETED
        }
    }

    // G07: "자란 댐 보러 가기"/"닫기" 둘 다 - 봤다고 표시하고 완료 화면으로
    suspend fun acknowledgeStageUp() {
        runCatching { ApiClient.cardHomeApi.markStageUpSeen() }
        stageUpPending.value = null
        step.value = CardHomeStep.COMPLETED
    }

    suspend fun loadToday() {
        step.value = CardHomeStep.LOADING
        errorMessage.value = null
        runCatching {
            val response = ApiClient.cardHomeApi.getTodayCards()
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { window ->
            setId.value = window.set_id
            optionIds.value = window.option_back_ids
            drawState.value = window.draw_state
            todayChallengeId.value = window.challenge_id
            todayChallengeState.value = window.challenge_state
            isTodayRestDay.value = window.is_rest_day
            pickedIndex.value = null
            showConfirmDialog.value = false
            // ⚠️ 쉬어가기 상태가 daily_record_notes에만 있고 카드 재조회 응답엔 없어서,
            // 앱을 재시작하거나 다른 탭 갔다 오면 쉬어가기 표시가 사라지던 버그를 고침 —
            // isTodayRestDay만 반영하고, 실제 화면 표시는 CardHomeScreen(홈)이 그 안에서
            // 마스코트 카드만 "쉼" 버전으로 바꿔서 보여줌(틈튼지수 요약 등 나머지는 그대로 유지).
            step.value = CardHomeStep.HOME
        }.onFailure { e ->
            errorMessage.value = e.message ?: "오늘의 카드를 가져오지 못했어요."
            step.value = CardHomeStep.ERROR
        }
        loadCompanion()
    }

    // B01b: "미션 이어하기" - 이미 확정된 오늘 챌린지의 카드 내용을 다시 불러와서 B06으로.
    suspend fun continueTodayMission() {
        val challengeId = todayChallengeId.value ?: return
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.cardHomeApi.revealChallenge(challengeId)
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { card ->
            revealedCard.value = card
            // ⚠️ 이미 완료(또는 건너뛴) 챌린지를 다시 "시작"하려고 하면 서버가 정당하게
            // 막아서(READY/PAUSED가 아니라 409) "측정을 시작하지 못했어요" 같은 혼란스러운
            // 에러로 이어졌음. 완료된 건이면 처음부터 완료 화면으로 보내서 그 상황 자체를 막음.
            // ⚠️ SKIPPED(중단하기로 끝낸 미션)도 COMPLETED와 같이 다시 "시작"할 수 없는
            // 종료 상태임. 서버(challenge_service.skip)가 SKIPPED -> ACTIVE 전이를 허용하지
            // 않아서, 여기서 안 막으면 REVEALED로 들어간 뒤 "이 행동 시작하기"를 눌렀을 때
            // 결국 startChallenge 호출이 409로 튕기며 "시작할 수 없는 상태입니다" 에러로 이어짐.
            step.value = when (card.state) {
                "COMPLETED", "SKIPPED" -> CardHomeStep.COMPLETED
                else -> CardHomeStep.REVEALED
            }
        }.onFailure { e ->
            errorMessage.value = e.message ?: "미션 정보를 가져오지 못했어요."
        }
        isLoading.value = false
    }

    suspend fun loadCompanion() {
        runCatching {
            val response = ApiClient.cardHomeApi.getCompanionStatus()
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { body ->
            companionStage.value = body.current_stage
            companionMaterialsNeeded.value = body.materials_needed_for_next
            companionNextStageLabel.value = body.stages.firstOrNull { !it.completed }?.label
            companionMaterials.value = body.materials
        }
    }

    // B03 -> B04: 카드 한 장을 "가운데로" 고름 (아직 서버에 확정 안 함)
    fun pickCard(index: Int) {
        pickedIndex.value = index
    }

    fun resetPick() {
        pickedIndex.value = null
        showConfirmDialog.value = false
    }

    // B04 -> B05: 확정 다이얼로그 열기
    fun openConfirmDialog() {
        showConfirmDialog.value = true
    }

    // B05: "확정하기" -> 실제 서버 호출
    suspend fun confirmCard() {
        val sid = setId.value ?: return
        val index = pickedIndex.value ?: return
        val optionId = optionIds.value.getOrNull(index) ?: return

        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.cardHomeApi.selectCard(sid, optionId)
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { reveal ->
            revealedCard.value = reveal
            showConfirmDialog.value = false
            step.value = CardHomeStep.REVEALED
        }.onFailure { e ->
            errorMessage.value = e.message ?: "카드 확정에 실패했어요."
            showConfirmDialog.value = false
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
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { result ->
            pointsAwarded.value = result.points_awarded
            goToCompletedOrCelebrate()
        }.onFailure { e ->
            errorMessage.value = e.message ?: "완료 처리에 실패했어요."
        }
        isLoading.value = false
    }

    // B16: 바텀시트 열 때 - 현재 이번 주 쉼 사용 현황을 먼저 불러옴
    suspend fun openRestDaySheet() {
        // ⚠️ RevealScreen에서 버튼 자체는 숨겼지만, CheckChallengeScreen/홈 화면 등 다른
        // 진입점에서도 이 함수를 공유해서 부르므로 여기서도 한 번 더 막아둠. 이미 완료/중단된
        // 미션인데 "쉬어가기"까지 확정되면 이번 주 쉼 횟수만 잘못 깎여나감(완료+쉼이 동시에
        // 찍히는 모순).
        val finished = revealedCard.value?.state == "COMPLETED" ||
            revealedCard.value?.state == "SKIPPED" ||
            todayChallengeState.value == "COMPLETED" ||
            todayChallengeState.value == "SKIPPED"
        if (finished) return

        runCatching {
            val response = ApiClient.cardHomeApi.getStreak()
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { streak ->
            restDaysUsedThisWeek.value = streak.rest_days_used_this_week
            restDaysRemainingThisWeek.value = streak.rest_days_remaining_this_week
            currentStreak.value = streak.current_streak
        }
        showRestDaySheet.value = true
    }

    // B16 -> B17: "오늘 쉬어가기" 확정 - 실제 서버 호출 (기록 캘린더 API 재사용)
    suspend fun confirmRestDay() {
        isLoading.value = true
        errorMessage.value = null
        val today = java.time.LocalDate.now().toString() // "YYYY-MM-DD"
        runCatching {
            val response = ApiClient.cardHomeApi.markRestDay(RestDayRequest(service_date = today))
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { streak ->
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
        }.onFailure { e ->
            errorMessage.value = e.message ?: "쉼 표시에 실패했어요."
        }
        isLoading.value = false
    }

    fun closeRestDaySheet() {
        // ⚠️ 예전엔 여기서도 step.value = CardHomeStep.HOME으로 보내서, RevealScreen이나
        // CheckChallengeScreen에서 열었어도 "닫기"만 누르면 무조건 홈으로 튕겼음. 이제
        // 오버레이라 그냥 닫기만 하면 원래 보고 있던 화면이 자연스럽게 그대로 보임.
        showRestDaySheet.value = false
    }

    // ===== C그룹: 챌린지 진행 (타이머형) =====

    // C02 -> C03: 시작
    // C02 -> C03: 시작 - 실제 서버 시작 API 호출 (여기서부터 서버가 READY -> ACTIVE로 바뀜)
    suspend fun startTimer() {
        val challengeId = revealedCard.value?.challenge_id ?: return
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.cardHomeApi.startChallenge(challengeId)
            if (!response.isSuccessful) error(parseErrorMessage(response))
        }.onSuccess {
            timerElapsedSeconds.value = 0
            timerIsPaused.value = false
            // ⚠️ revealedCard.state를 서버와 맞춰줘야, 나중에 뒤로가기 후 다시 "이 행동
            // 시작하기"를 눌렀을 때(onStartAction) 이미 ACTIVE인 걸 알고 TIMER_START(0:00,
            // 시작하기 버튼)로 또 안 보내고 CHALLENGE_TIMER_RUNNING으로 바로 이어줄 수 있음.
            // 이걸 안 갱신하면 서버는 ACTIVE인데 클라만 모르고 startChallenge를 또 호출해서
            // "이미 ACTIVE라 READY/PAUSED에서만 가능한 시작 전이가 거부됨(409)"으로 이어짐.
            revealedCard.value = revealedCard.value?.copy(state = "ACTIVE")
            step.value = CardHomeStep.CHALLENGE_TIMER_RUNNING
        }.onFailure { e ->
            errorMessage.value = e.message ?: "시작하지 못했어요."
        }
        isLoading.value = false
    }

    // C03 -> C04: 일시정지 - 실제 서버 일시정지 API 호출
    suspend fun pauseTimer() {
        val challengeId = revealedCard.value?.challenge_id
        timerIsPaused.value = true
        revealedCard.value = revealedCard.value?.copy(state = "PAUSED")
        step.value = CardHomeStep.CHALLENGE_TIMER_PAUSED
        if (challengeId != null) {
            runCatching { ApiClient.cardHomeApi.pauseChallenge(challengeId) }
        }
    }

    // C04 -> C03: 이어서 하기 - 실제 서버 재시작 API 호출 (지금까지 쌓인 시간은 그대로 유지)
    suspend fun resumeTimer() {
        val challengeId = revealedCard.value?.challenge_id
        if (challengeId != null) {
            runCatching { ApiClient.cardHomeApi.startChallenge(challengeId) }
        }
        revealedCard.value = revealedCard.value?.copy(state = "ACTIVE")
        timerIsPaused.value = false
        step.value = CardHomeStep.CHALLENGE_TIMER_RUNNING
    }

    // C19: "그만두기" - 실제 서버에 건너뛰기로 기록 (오늘 이 행동은 쉼으로 남음)
    suspend fun quitChallenge() {
        val challengeId = revealedCard.value?.challenge_id
        showQuitDialog.value = false
        if (challengeId != null) {
            runCatching {
                ApiClient.cardHomeApi.skipChallenge(
                    challengeId,
                    com.tmtn.app.network.model.SkipChallengeRequest(reason = "사용자가 중단함")
                )
            }
        }
        loadToday()
    }

    // C03/C04 -> C05 -> (성공하면) C20: 목표를 채운 뒤 완료 처리
    suspend fun completeTimerChallenge() {
        step.value = CardHomeStep.CHALLENGE_PROCESSING
        val challengeId = revealedCard.value?.challenge_id ?: return
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.cardHomeApi.completeChallenge(
                challengeId, UUID.randomUUID().toString(), CompleteChallengeRequestBody()
            )
            if (!response.isSuccessful) error(parseErrorMessage(response))
            response.body()!!
        }.onSuccess { result ->
            pointsAwarded.value = result.points_awarded
            step.value = CardHomeStep.CHALLENGE_RETROSPECT
        }.onFailure { e ->
            errorMessage.value = e.message ?: "완료 처리에 실패했어요."
            // ⚠️ 여기서 무조건 CHALLENGE_TIMER_RUNNING으로 보내던 게 버그였음. CHECK형은
            // 원래 타이머 화면에 들어간 적도 없는데 실패하면 갑자기 타이머가 도는 화면으로
            // 튕겨서, "완료하기 눌렀는데 왜 타이머가 시작되냐"는 혼란 + 그 화면에서 다시
            // 시도해도 계속 실패하는 문제로 이어졌음. exec_type에 맞는 원래 화면으로 되돌림.
            step.value = when (revealedCard.value?.exec_type) {
                "CHECK" -> CardHomeStep.CHALLENGE_CHECK_CONFIRM
                else -> CardHomeStep.CHALLENGE_TIMER_RUNNING
            }
        }
        isLoading.value = false
    }

    // C20: 한 줄 회고 저장 (기록 캘린더의 메모 API 재사용) - 저장/건너뛰기 둘 다 결국 완료 화면으로
    suspend fun submitRetrospect(memo: String?) {
        if (!memo.isNullOrBlank()) {
            val today = java.time.LocalDate.now().toString()
            runCatching {
                ApiClient.cardHomeApi.updateDayMemo(today, MemoUpdateRequest(memo = memo))
            }.onSuccess { hasMemoToday.value = true }
        }
        goToCompletedOrCelebrate()
    }

    // B07: "한 줄 남기기" 버튼을 보여줄지 판단 - 오늘 이미 회고를 남겼으면 버튼 자체를 숨김.
    suspend fun checkTodayMemo() {
        val today = java.time.LocalDate.now().toString()
        runCatching {
            val response = ApiClient.recordApi.getDayDetail(today)
            if (response.isSuccessful) response.body() else null
        }.getOrNull()?.let { detail ->
            hasMemoToday.value = !detail.memo.isNullOrBlank()
        }
    }
}
