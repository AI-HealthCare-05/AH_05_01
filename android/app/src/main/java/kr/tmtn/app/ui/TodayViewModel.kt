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
import kr.tmtn.app.domain.model.DamMaterial
import kr.tmtn.app.domain.model.DamStage
import kr.tmtn.app.domain.model.DamStages
import kr.tmtn.app.domain.model.DayStatus
import kr.tmtn.app.domain.model.REST_PER_WEEK
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

    /* ------------------------------------------------- 회고 · 적합도 */

    var reflection by mutableStateOf(container.store.reflectionOn(TmtnDate.todayKey()))
        private set

    var fit by mutableStateOf(container.store.fitOn(TmtnDate.todayKey()))
        private set

    /** 지난 날의 회고를 읽는다. 기록 탭의 하루 상세에서 쓴다. */
    fun reflectionOf(date: String): String = container.store.reflectionOn(date)
    fun fitOf(date: String): String = container.store.fitOn(date)

    /** 한 줄과 적합도는 선택이다. 비워 두어도 기록은 이미 남아 있다. */
    fun saveReflection(note: String, fitAnswer: String) {
        container.store.saveReflection(dateKey, note, fitAnswer)
        reflection = note
        fit = fitAnswer
    }

    /* ------------------------------------------------------ 대체 미션 */

    /** 오늘 이미 한 번 바꿨나 */
    var swappedToday by mutableStateOf(container.store.swappedOn(TmtnDate.todayKey()))
        private set

    /** 아직 바꿀 수 있나. 이미 끝냈거나 한 번 썼으면 못 바꾼다. */
    fun canSwapToday(): Boolean = !swappedToday && !isDoneToday() && picked != null

    /**
     * 오늘 카드를 같은 축의 다른 미션으로 바꾼다. **하루 한 번.**
     * 바꿀 후보가 없으면 아무 일도 하지 않고 false 를 준다.
     */
    fun swapMission(): Boolean {
        if (!canSwapToday()) return false
        val current = picked ?: return false
        val alt = DailyCardDraw.alternative(container.catalog, current, dateKey) ?: return false
        container.store.savePick(dateKey, alt.id, alt.targetNumber, alt.place)
        container.store.markSwapped(dateKey)
        picked = alt
        swappedToday = true
        return true
    }

    /* ------------------------------------------------------------ 쉼 */

    /** 오늘을 쉼으로 표시했나 */
    var isRestToday by mutableStateOf(container.store.isRest(TmtnDate.todayKey()))
        private set

    /** 이번 주에 이미 쉰 횟수 (월요일 시작) */
    fun restUsedThisWeek(): Int = container.store.restDaysInWeek(TmtnDate.thisWeek()).size

    /** 이번 주에 남은 쉼 횟수 */
    fun restLeftThisWeek(): Int = (REST_PER_WEEK - restUsedThisWeek()).coerceAtLeast(0)

    /**
     * 오늘을 쉼으로 표시한다.
     * 이미 미션을 끝냈거나 이번 주 몫을 다 썼으면 아무 일도 하지 않는다 —
     * 화면에서 미리 막지만, 여기서도 한 번 더 막는다.
     */
    fun markRestToday(): Boolean {
        if (isDoneToday() || isRestToday || restLeftThisWeek() <= 0) return false
        container.store.markRest(dateKey)
        isRestToday = true
        return true
    }

    /** 잘못 눌렀을 때 되돌리기 */
    fun undoRestToday() {
        container.store.clearRest(dateKey)
        isRestToday = false
    }

    fun statusOf(date: String): DayStatus = when {
        records.any { it.date == date } -> DayStatus.DONE
        container.store.isRest(date) -> DayStatus.REST
        TmtnDate.isFuture(date) -> DayStatus.FUTURE
        date == dateKey -> DayStatus.FUTURE   // 오늘은 아직 지나지 않았다. 미완료로 몰지 않는다
        else -> DayStatus.MISSED
    }

    /**
     * 연속 기록. 오늘부터 거슬러 올라가며 센다.
     *
     * **쉼은 끊지 않고 건너뛴다** — 대신 연속일수로 세지도 않는다.
     * 미완료를 만나면 거기서 멈춘다.
     */
    fun streak(): Int {
        var n = 0
        var cursor = java.time.LocalDate.now()
        // 오늘을 아직 안 했다고 연속이 깨진 것은 아니다. 어제부터 본다.
        if (statusOf(cursor.toString()) != DayStatus.DONE) cursor = cursor.minusDays(1)
        while (true) {
            when (statusOf(cursor.toString())) {
                DayStatus.DONE -> n++
                DayStatus.REST -> Unit          // 건너뛴다
                else -> return n
            }
            cursor = cursor.minusDays(1)
            if (n > 400) return n               // 안전장치
        }
    }

    fun resetDemo() {
        container.store.resetAll()
        lastCompleted = null
        refresh()
    }

    /* ------------------------------- 댐 · 재료 (G01 · G02 · G05 · G06) */

    /** 지금까지 모은 재료 수. **완료 기록 하나가 재료 하나다.** */
    fun collectedTotal(): Int = records.size

    /**
     * 지금 댐이 몇 단계인지. 문턱값은 피그마 G03 기준 (5 · 15 · 35 · 70 · 120).
     * 댐 탭(G01)·주간 리포트(D02)·축하(G07) 가 **모두 이 값을 써야** 숫자가 어긋나지 않는다.
     */
    fun damStage(): DamStage = DamStages.of(collectedTotal())

    /** G02 재료 도감 — 5종을 **0개인 것까지 전부** 정해진 순서로 준다 */
    fun materialSummary(): List<MaterialCount> {
        val counted = records.mapNotNull { DamMaterial.from(it.rewardName) }
            .groupingBy { it }.eachCount()
        return DamMaterial.displayOrder.map { MaterialCount(it, counted[it] ?: 0) }
    }

    /** G05 재료별 상세 — 그 재료를 받은 기록을 최근 것부터 [limit] 개까지 */
    fun materialDetail(material: DamMaterial, limit: Int = 5): MaterialDetail {
        val mine = records
            .filter { DamMaterial.from(it.rewardName) == material }
            .sortedByDescending { it.date }
        return MaterialDetail(
            material = material,
            count = mine.size,
            logs = mine.take(limit).map {
                MaterialLog(
                    title = it.title,
                    whenLabel = "${TmtnDate.label(it.date)} ${it.completedAtLabel}".trim(),
                    record = it,
                )
            },
            totalLogs = mine.size,
        )
    }

    /** G06 카드첩 — 모은 카드 전부(최근 것부터). [filter] 를 주면 그 재료만 */
    fun collectedCards(filter: DamMaterial? = null): List<CollectedCard> =
        records.sortedByDescending { it.date }
            .map { CollectedCard(it, DamMaterial.from(it.rewardName), TmtnDate.label(it.date)) }
            .filter { filter == null || it.material == filter }

    /* -------------------------------------------- 주간 리포트 (D02) */

    /**
     * 한 주치 요약. [weekOffset] 0 이 이번 주, -1 이 지난주다.
     * D02 의 "지난 리포트 ›" 는 이 값을 하나씩 줄여 부르면 된다.
     *
     * 주는 **월요일에 시작한다** — 홈의 주간 점, 기록 달력과 같은 경계다.
     */
    fun weekReport(weekOffset: Int = 0): WeekReport {
        val days = TmtnDate.weekOf(weekOffset)
        val cells = days.map { d ->
            WeekDayCell(
                dateKey = d,
                dayOfMonth = TmtnDate.dayOf(d),
                weekdayShort = TmtnDate.weekdayShort(d),
                status = statusOf(d),
                isToday = d == dateKey,
            )
        }
        // 오늘은 아직 할 수 있으므로 "남은 날" 에 넣지 않는다.
        val remaining = days.count { TmtnDate.isFuture(it) }

        val weekRecords = records.filter { it.date in days }
        val counted = weekRecords.mapNotNull { DamMaterial.from(it.rewardName) }
            .groupingBy { it }.eachCount()

        val done = cells.count { it.status == DayStatus.DONE }
        val rest = cells.count { it.status == DayStatus.REST }

        return WeekReport(
            weekOffset = weekOffset,
            weekStartKey = days.first(),
            rangeLabel = TmtnDate.rangeLabel(days.first(), days.last()),
            days = cells,
            doneCount = done,
            restCount = rest,
            missedCount = cells.count { it.status == DayStatus.MISSED },
            remainingCount = remaining,
            materials = DamMaterial.displayOrder.mapNotNull { m ->
                counted[m]?.let { MaterialCount(m, it) }
            },
            materialsThisWeek = weekRecords.size,
            collectedTotal = collectedTotal(),
            dam = damStage(),
            beaverLine = beaverLine(done, rest, remaining, weekOffset == 0),
            isThisWeek = weekOffset == 0,
        )
    }

    /**
     * 비버 한마디.
     *
     * TODO(기획): 문구는 임시다. D02 시안의 말투("한 번 쉬고 두 번 해냈어.")를 흉내 낸 것이니
     *  최종 문구는 기획에서 확정해 주세요. **쉼을 탓하는 말은 쓰지 않는다** 는 원칙만 지키면 된다.
     */
    private fun beaverLine(done: Int, rest: Int, remaining: Int, isThisWeek: Boolean): String {
        val counts = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱")
        val spans = listOf("", "하루", "이틀", "사흘", "나흘", "닷새", "엿새", "이레")
        fun c(v: Int) = counts.getOrElse(v) { "$v" }

        val head = when {
            done == 0 && rest == 0 && isThisWeek -> "이번 주는 이제 시작이야."
            done == 0 && rest == 0 -> "이 주에는 기록이 없어."
            done == 0 -> "${c(rest)} 번 쉬어 갔어. 그것도 괜찮아."
            rest == 0 -> "${c(done)} 번 해냈어."
            else -> "${c(rest)} 번 쉬고 ${c(done)} 번 해냈어."
        }
        val span = spans.getOrElse(remaining) { "${remaining}일" }
        val tail = when {
            !isThisWeek -> ""
            remaining > 0 -> "\n아직 $span 남았으니 천천히 가."
            else -> "\n한 주 잘 마쳤어."
        }
        return head + tail
    }

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
