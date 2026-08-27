package kr.tmtn.app.domain.ml

/**
 * 모델 3 — 틈튼지수.
 *
 * 나이대·성별·키·몸무게·허리둘레(모델 1의 값도 가능)·운동량으로
 * 고혈압·당뇨 위험도를 점수 한 개로 보여준다.
 *
 * 반드시 지켜야 할 것 (프로젝트 지침 4-3, DESIGN.md):
 *  - **진단이 아니다.** 화면에 항상 비진단 고지가 붙는다.
 *  - 점수가 내려간 것을 "건강해졌다" · "위험이 낮아졌다" 로 쓰지 않는다.
 *    입력값이 바뀌어 다시 계산된 것뿐이다.
 *  - 챌린지 완료만으로 점수를 움직이지 않는다. 건강 입력값이 바뀔 때만 재계산한다.
 *  - 오행 기운·챌린지 이행률과 절대 합산하지 않는다. 세 체계는 분리한다.
 */
data class TmtnIndexInput(
    val birthYear: Int,
    val sex: Sex?,
    val heightCm: Double,
    val weightKg: Double,
    val waistCm: Double?,
    /** true = 허리둘레가 모델 1의 추정값이다. 화면에 그렇게 표시해야 한다. */
    val waistFromModel: Boolean,
    val aerobicMinutesPerWeek: Int,
    val strengthDaysPerWeek: Int,
    /** 최근 7일 앱에서 기록된 활동 시간(분) */
    val recentActiveMinutes7d: Int,
)

enum class IndexBand(val label: String) {
    LOW("낮은 구간"), NORMAL("보통 구간"), WATCH("살펴볼 구간"),
}

/** 무엇이 점수에 얼마나 기여했는지. XAI 레이어가 채운다. */
data class IndexFactor(
    val name: String,
    /** -1.0 ~ +1.0. 양수면 점수를 올린 요인. */
    val contribution: Double,
    val explanation: String,
)

data class TmtnIndexResult(
    val score: Int,
    val band: IndexBand,
    val factors: List<IndexFactor>,
    val windowLabel: String,
    /** 화면 하단에 항상 붙는 문장. 빈 문자열로 두지 말 것. */
    val disclaimer: String = "비진단용 참고 정보입니다. 진료를 대신하지 않습니다.",
)

interface TmtnIndexScorer {
    val info: ModelInfo
    suspend fun score(input: TmtnIndexInput): ModelResult<TmtnIndexResult>
}
