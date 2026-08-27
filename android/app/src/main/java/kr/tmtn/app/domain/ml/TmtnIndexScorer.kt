package kr.tmtn.app.domain.ml

/**
 * 모델 ③ 틈튼지수.
 *
 * ⚠️ **AI 팀 계약서에서 이 지수는 명시적으로 범위 밖입니다.**
 * model_experiment_protocol_v0_2.yaml
 * ```
 * objective.tmtn_index:
 *   included: false
 *   reason: separately_validated_composite_formula_required
 * ```
 * 즉 강호님 파이프라인은 틈튼지수를 만들어 주지 않습니다.
 * 별도로 검증된 합성 공식을 팀이 따로 정의하고 승인해야 합니다.
 * 그 전까지 이 자리는 **임시 규칙 점수**로 남습니다.
 *
 * 계약서가 실제로 내주는 출력은 아래 셋뿐입니다.
 *   - estimated_waist_cm
 *   - diabetes_measurement_abnormality_probability
 *   - hypertension_measurement_abnormality_probability
 *
 * 그리고 그 확률의 의미는 **"지금 이 한 번의 측정이 기준치를 벗어날 가능성"** 입니다.
 * `disease_output_semantics: current_single_visit_measurement_abnormality_reference`
 *
 * 절대 하면 안 되는 해석 (`prohibited_interpretations`)
 *   - future_incidence_probability   → "앞으로 걸릴 확률" 로 쓰지 않는다
 *   - clinical_diagnosis             → 진단으로 쓰지 않는다
 *   - treatment_recommendation       → 치료 권고로 쓰지 않는다
 */

/** 질환별 측정 이상 확률. 계약서가 실제로 내주는 출력. */
data class MeasurementAbnormality(
    /** 0.0 ~ 1.0 */
    val probability: Double,
    /** 어떤 모델 버전이 냈는지 */
    val modelVersion: String,
)

data class TmtnIndexInput(
    val ageYears: Int,
    val sexCode: SexCode?,
    val heightCm: Double,
    val weightKg: Double,

    /**
     * ★ **모델이 추정한 허리둘레만 넣는다.**
     * protocol_v0_2 `features.estimated_waist_cm.measured_waist_as_disease_input: prohibited`
     * 사용자가 줄자로 잰 값은 화면 표시용이지 모델 입력이 아니다.
     * 앱은 줄자로 잰 값을 아예 받지 않으므로, 여기 들어오는 값은 언제나 추정값이다.
     */
    val estimatedWaistCm: Double?,
    /** 위 값을 만든 추정기 버전. 기록용 */
    val waistEstimatorVersion: String?,

    /** 유산소 — 강도 환산 후 값. MET-분이 아니다 */
    val leisureAerobicModerateEquivalentMinWeek: Double?,
    val strengthDaysWeek: Int?,

    /** 앱에서 실제로 기록된 최근 7일 활동 시간(분). 조사 자료가 아니라 앱 기록이다 */
    val recentActiveMinutes7d: Int,

    val pregnancyStatus: PregnancyStatus = PregnancyStatus.UNKNOWN,
)

enum class IndexBand(val label: String) {
    LOW("낮은 구간"), NORMAL("보통 구간"), WATCH("살펴볼 구간"),
}

/** 무엇이 점수에 얼마나 기여했는지. XAI 레이어가 채운다. */
data class IndexFactor(
    val name: String,
    /** -1.0 ~ +1.0. 양수면 점수를 올린 요인 */
    val contribution: Double,
    val explanation: String,
)

data class TmtnIndexResult(
    val score: Int,
    val band: IndexBand,
    val factors: List<IndexFactor>,
    val windowLabel: String,
    /** 허리둘레를 모델 추정값으로 계산했는지 (계약상 항상 true 여야 한다) */
    val usedEstimatedWaist: Boolean,
    /** 화면 하단에 항상 붙는 문장. 빈 문자열로 두지 말 것 */
    val disclaimer: String =
        "비진단용 참고 정보입니다. 지금 상태를 조사 자료와 견줘 본 값이며, " +
            "앞으로 질환이 생길 확률이 아닙니다. 진료를 대신하지 않습니다.",
)

interface TmtnIndexScorer {
    val info: ModelInfo
    suspend fun score(input: TmtnIndexInput): ModelResult<TmtnIndexResult>
}
