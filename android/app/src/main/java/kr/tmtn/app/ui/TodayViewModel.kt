package kr.tmtn.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kr.tmtn.app.data.AppContainer
import kr.tmtn.app.data.DailyCardDraw
import kr.tmtn.app.domain.ml.*
import kr.tmtn.app.domain.model.DailyRecord
import kr.tmtn.app.domain.model.MissionCard
import kr.tmtn.app.domain.model.UserProfile
import kotlinx.coroutines.launch

/**
 * 오늘 하루의 상태를 한곳에 모은다 — 프로필, 오늘의 카드 세 장, 고른 카드, 기록, 틈튼지수.
 *
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

    fun login() {
        container.store.isLoggedIn = true
    }

    fun logout() {
        container.store.logout()
    }

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
    }

    fun isDoneToday(): Boolean = records.any { it.date == dateKey }

    fun resetDemo() {
        container.store.resetAll()
        lastCompleted = null
        refresh()
    }

    /** 재료를 몇 개 모았는지 (댐 탭에서 쓴다) */
    fun materialCounts(): Map<String, Int> =
        records.groupingBy { it.rewardName }.eachCount()

    /**
     * 모델 1 → 모델 3 순서로 다시 계산한다.
     * 챌린지를 완료했다고 해서 부르지 않는다. 건강 입력값이 바뀔 때만 부른다.
     */
    private fun recompute() {
        val p = profile
        if (!p.isComplete) {
            indexState = ModelResult.NotReady("키와 몸무게를 입력하면 계산해요.")
            waistState = null
            return
        }
        viewModelScope.launch {
            var waist = p.waistCm
            var waistFromModel = false

            if (waist == null) {
                val r = ModelRegistry.waistEstimator.estimate(
                    WaistInput(p.birthYear, p.sex, p.heightCm, p.weightKg),
                )
                waistState = r
                if (r is ModelResult.Ready) {
                    waist = r.value.waistCm
                    waistFromModel = true
                }
            } else {
                waistState = null
            }

            val recent7 = records
                .filter { TmtnDate.lastDays(7).contains(it.date) && it.unit == "분" }
                .sumOf { it.achieved }

            indexState = ModelRegistry.indexScorer.score(
                TmtnIndexInput(
                    birthYear = p.birthYear,
                    sex = p.sex,
                    heightCm = p.heightCm,
                    weightKg = p.weightKg,
                    waistCm = waist,
                    waistFromModel = waistFromModel,
                    aerobicMinutesPerWeek = p.aerobicMinutesPerWeek,
                    strengthDaysPerWeek = p.strengthDaysPerWeek,
                    recentActiveMinutes7d = recent7,
                ),
            )
        }
    }
}
