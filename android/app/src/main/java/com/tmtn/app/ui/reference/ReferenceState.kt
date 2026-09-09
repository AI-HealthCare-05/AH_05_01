package com.tmtn.app.ui.reference

import androidx.compose.runtime.mutableStateOf
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.ScoreInputsResponse
import com.tmtn.app.network.model.TuntunScorePeerV2Response

/** E01~E06 "틈튼지수" 탭 전체 단계.
 * ⚠️ E06(이 지수에 대하여)은 HANDOFF.md 기준 "진입 경로가 없음"(마이/설정 쪽 추정) —
 * 이 플로우 안에서는 못 들어가고, 나중에 F그룹(내정보/설정)에서 별도로 연결해야 함. */
enum class ReferenceStep {
    LOADING,
    SUMMARY,       // E01
    INELIGIBLE,    // E05 - 산출 불가
    DETAIL,        // E02
    FACTORS,       // E03
    INPUTS,        // E04
    ABOUT,         // E06 - 이 플로우 밖 F그룹에서 진입 예정, 여기선 미연결
}

/** E그룹 전체 상태. CardHomeState/RecordState와 같은 패턴 — 백스택은 E04가
 * E01(요약)·E03(반영 항목) 양쪽에서 들어올 수 있어서 맵 대신 실제 스택으로 관리함. */
class ReferenceState {
    private val backStack = mutableListOf(ReferenceStep.LOADING)
    val step = mutableStateOf(backStack.last())

    val score = mutableStateOf<TuntunScorePeerV2Response?>(null)
    val eligibleRecordedDaysLabel = mutableStateOf("")
    val eligibleRequiredDaysLabel = mutableStateOf("")
    val scoreInputs = mutableStateOf<ScoreInputsResponse?>(null)
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)

    private fun push(next: ReferenceStep) {
        backStack.add(next)
        step.value = next
    }

    /** 시스템/화면 뒤로가기 공통 처리. 스택에 더 없으면 false를 돌려줘서
     * 상위(MainActivity)가 홈 탭으로 보내게 함. */
    fun goBack(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        step.value = backStack.last()
        return true
    }

    // ⚠️ 2026-09-09 반영: 실모델(또래 백분위) 연동. 기존 v2(Mock)는 "최근 7일 기록 일수"로
    // 산출 가능 여부를 판정했는데, 새 계약엔 그 개념이 없음(availableComponentCount로만
    // 판정) - 그대로 재사용하지 않음.
    suspend fun loadScore() {
        isLoading.value = true
        errorMessage.value = null
        runCatching {
            val response = ApiClient.tuntunScoreApi.getTuntunScorePeerV2()
            if (!response.isSuccessful) error("틈튼지수를 불러오지 못했어요.")
            response.body()!!
        }.onSuccess { result ->
            if (result.scoreAvailable) {
                score.value = result
                replaceRoot(ReferenceStep.SUMMARY)
            } else {
                eligibleRequiredDaysLabel.value = "신체정보 또는 운동습관"
                replaceRoot(ReferenceStep.INELIGIBLE)
            }
        }.onFailure { e ->
            errorMessage.value = e.message ?: "틈튼지수를 불러오지 못했어요."
        }
        isLoading.value = false
    }

    private fun replaceRoot(rootStep: ReferenceStep) {
        backStack.clear()
        backStack.add(rootStep)
        step.value = rootStep
    }

    // E01 "자세히 >" -> E02
    fun openDetail() = push(ReferenceStep.DETAIL)

    // E02 "이번 계산에 반영된 항목 보기" -> E03
    fun openFactors() = push(ReferenceStep.FACTORS)

    // E01 "계산에 쓰인 값 보기" 또는 E03 "값 다시 입력하기" -> E04
    fun openInputs() {
        push(ReferenceStep.INPUTS)
        // 진입할 때마다 최신 값 다시 조회 (F/A그룹에서 수정하고 돌아왔을 수 있음)
    }

    suspend fun loadInputs() {
        isLoading.value = true
        runCatching {
            val response = ApiClient.tuntunScoreApi.getTuntunScoreInputs()
            if (!response.isSuccessful) error("입력값을 불러오지 못했어요.")
            response.body()!!
        }.onSuccess { scoreInputs.value = it }
            .onFailure { e -> errorMessage.value = e.message ?: "입력값을 불러오지 못했어요." }
        isLoading.value = false
    }

    // E04 "값 고치고 다시 계산" - 편집은 F/A그룹 화면에서 이미 저장됐으므로, 여기선 최신
    // 상태로 다시 불러와서 E01로 돌아가는 역할만 함 (재계산 자체는 서버가 조회 시점에 매번 함).
    suspend fun recalculateAndReturnToSummary() {
        loadScore()
    }
}
