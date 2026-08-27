package kr.tmtn.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kr.tmtn.app.data.AppContainer
import kr.tmtn.app.data.DailyCardDraw
import kr.tmtn.app.domain.ml.ModelRegistry
import kr.tmtn.app.domain.ml.ModelResult
import kr.tmtn.app.domain.ml.TmtnIndexInput
import kr.tmtn.app.domain.ml.TmtnIndexResult
import kr.tmtn.app.domain.ml.WaistEstimate
import kr.tmtn.app.domain.model.DailyRecord
import kr.tmtn.app.domain.model.MissionCard
import kr.tmtn.app.domain.model.UserProfile

/**
 * 오늘 하루의 상태를 한곳에 모은다 — 프로필, 오늘의 카드 세 장, 고른 카드, 기록, 건강 참고 정보.
 * 화면 여러 개가 같은 인스턴스를 본다 (MainActivity 에서 한 번 만들어 내려 준다).
 */
class TodayViewModel(private val container: AppContainer) : ViewModel() {

    val dateKey: String = TmtnDate.todayKey()

    var profile by mutableStateOf(container.store.loadProfile())
        private set

    var cards by mutableStateOf<List<MissionCard>>(emptyList())
        private set

    /** 오늘 확정한 카드. 확정하면 바꿀 수 없다. */
    var picked by mutableStateOf<MissionCard?>(null)
        private set

    var records by mutableStateOf<List<DailyRecord>>(emptyList())
        private set

    var lastCompleted by mutableStateOf<DailyRecord?>(null)

    var indexState by mutableStateOf<ModelResult<TmtnIndexResult>>(
        ModelResult.NotReady("건강 정보를 입력하면 계산해요."),
    )
        private set

    /** 모델 ① 추정 결과 그대로 */
    var waistState by mutableStateOf<ModelResult<WaistEstimate>?>(null)
        private set


    val isLoggedIn: Boolean get() = container.store.isLoggedIn
    val needsOnboarding: Boolean get() = !profile.isComplete

    init { refresh() }

    fun refresh() {
        profile = container.store.loadProfile()
        records = container.store.records()
        cards = DailyCardDraw.draw(container.catalog, profile, dateKey)

        val pickedId = container.store.pickedMissionId(dateKey)
        picked = pickedId?.let {
            DailyCardDraw.rebuild(
                container.catalog, it,
                container.store.pickedNumber(dateKey),
                container.store.pickedPlace(dateKey),
            )
        }
        recompute()
    }

    fun login() { container.store.isLoggedIn = true }

    fun logout() { container.store.logout() }

    fun saveProfile(p: UserProfile) {
        container.store.saveProfile(p)
        refresh()
    }

    fun confirmPick(card: MissionCard) {
        container.store.savePick(dateKey, card.id, card.targetNumber, card.place)
        picked = card
    }

    fun complete(achieved: Int, measuredByModel: Boolean, fromPlaceholder: Boolean) {
        val card = picked ?: return
        val record = DailyRecord(
            date = dateKey,
            missionId = card.id,
            title = card.title,
            axis = card.mission.axis,
            type = card.type,
            targetNumber = card.targetNumber,
            unit = card.mission.unit,
            achieved = achieved,
            completedAtLabel = TmtnDate.timeLabel(),
            rewardName = card.mission.rewardName,
            rewardHint = card.mission.rewardHint,
            measuredByModel = measuredByModel,
            fromPlaceholderModel = fromPlaceholder,
        )
        container.store.addRecord(record)
        lastCompleted = record
        records = container.store.records()
        // ★ 챌린지를 끝냈다고 건강 참고 정보를 다시 계산하지 않는다.
        //   건강 입력값이 바뀔 때만 recompute() 를 부른다 (프로젝트 지침 4-2).
    }

    fun isDoneToday(): Boolean = records.any { it.date == dateKey }

    fun resetDemo() {
        container.store.resetAll()
        lastCompleted = null
        refresh()
    }

    /** 재료를 몇 개 모았는지 (댐 탭에서 쓴다) */
    fun materialCounts(): Map<String, Int> = records.groupingBy { it.rewardName }.eachCount()

    /**
     * 모델 ① → 모델 ③ 순서로 다시 계산한다.
     * **건강 입력값이 바뀔 때만** 부른다.
     */
    private fun recompute() {
        val p = profile
        val waistInput = p.toWaistInput()
        if (waistInput == null) {
            indexState = ModelResult.NotReady("키와 몸무게를 입력하면 계산해요.")
            waistState = null
            return
        }

        // 대상 인구가 아니면 두 모델 모두 점수를 내지 않는다.
        waistInput.unsupportedReason()?.let { reason ->
            waistState = ModelResult.UnsupportedPopulation(reason)
            indexState = ModelResult.UnsupportedPopulation(reason)
            return
        }

        viewModelScope.launch {
            // ── 모델 ① 허리둘레 ─────────────────────────────
            val waistResult = ModelRegistry.waistEstimator.estimate(waistInput)
            waistState = waistResult
            val estimate = (waistResult as? ModelResult.Ready)?.value

            // ── 모델 ③ 틈튼지수 ─────────────────────────────
            val recent7 = records
                .filter { TmtnDate.lastDays(7).contains(it.date) && it.unit == "분" }
                .sumOf { it.achieved }

            indexState = ModelRegistry.indexScorer.score(
                TmtnIndexInput(
                    ageYears = p.ageYears,
                    sexCode = p.sexCode,
                    heightCm = p.heightCm,
                    weightKg = p.weightKg,
                    // 허리둘레는 오직 모델 추정값뿐이다. 앱은 잰 값을 아예 받지 않는다.
                    estimatedWaistCm = estimate?.waistCm,
                    waistEstimatorVersion = estimate?.estimatorVersion,
                    leisureAerobicModerateEquivalentMinWeek =
                        waistInput.leisureAerobicModerateEquivalentMinWeek,
                    strengthDaysWeek = p.strengthDaysWeek,
                    recentActiveMinutes7d = recent7,
                    pregnancyStatus = p.pregnancyStatus,
                ),
            )
        }
    }
}
