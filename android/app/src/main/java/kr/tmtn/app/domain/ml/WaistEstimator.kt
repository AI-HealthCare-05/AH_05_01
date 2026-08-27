package kr.tmtn.app.domain.ml

import kotlin.math.roundToInt

/**
 * 모델 ① 허리둘레 추정.
 *
 * ★ 이 파일은 AI 팀 계약서에 맞춰 작성했습니다. 앱 편의로 바꾸지 마세요.
 *   - 피처 6개와 이름: model_experiment_protocol_v0_2.yaml `features`
 *   - 앱 입력 원본:    data_contract_v0_3.yaml `app_raw_inputs`
 *   - 유산소 환산식:   activity_feature_contract_v0_1.yaml `app_aligned_leisure_feature`
 *   - 서빙 정책:       data_contract_v0_3.yaml `waist_serving_policy`
 *
 * 동결 기준(2026-08-26 WAIST_OOF_DEVELOPMENT_V0_1_FREEZE_RECORD):
 *   입력 tier W2 · random_forest_regressor(max_depth=12, min_samples_leaf=5, max_features=0.7)
 *   seed 42 · 개발 연도 2019~2021 · 2022 release gate 미수행 · production 승인 아님
 */

/** 모델이 받는 입력 tier. protocol_v0_2 `waist_submodel.input_tiers: [W0, W2]` */
enum class WaistInputTier(val features: List<String>) {
    /** F0_fallback — 운동 정보가 없을 때 */
    W0(listOf("age_years", "sex_code", "height_cm", "weight_kg")),

    /** F2_leisure — 여가 유산소·근력까지 있을 때. 동결 기록에서 W0보다 MAE 가 낮아 주 후보로 채택됨 */
    W2(
        listOf(
            "age_years", "sex_code", "height_cm", "weight_kg",
            "leisure_aerobic_moderate_equivalent_min_week", "strength_days_week",
        ),
    ),
}

/**
 * 앱이 사용자에게서 직접 받는 값들. `app_raw_inputs` 와 1:1 로 맞췄다.
 *
 * 주의 — 계약서 `missing_data.convert_unknown_to_zero: false`
 * 모르는 값을 0 으로 바꾸지 않는다. 모르면 null 로 둔다.
 */
data class WaistInput(
    /** age_years — 최소 19세 */
    val ageYears: Int,

    /** sex_code — 필수. 6개 모델 피처 중 하나 */
    val sexCode: SexCode?,

    val heightCm: Double,
    val weightKg: Double,

    /**
     * 유산소 — **강도별 주당 분** (피그마 A08 화면 그대로).
     * 저강도는 모델 공식에 들어가지 않으므로 여기 받지 않는다.
     */
    val leisureModerateMinWeek: Int? = null,
    val leisureVigorousMinWeek: Int? = null,

    /** strength_days_week — 0~5. 5 는 "주 5회 이상"(top-code) */
    val strengthDaysWeek: Int? = null,

    /** 근력 일수가 top-code(주 5회 이상)인지. 계약서 `emit_top_coded_flag: true` */
    val strengthTopCoded: Boolean = false,

    /** 모델 피처가 아니다. 자격·안전 판단에만 쓴다. */
    val pregnancyStatus: PregnancyStatus = PregnancyStatus.UNKNOWN,
) {
    /**
     * leisure_aerobic_moderate_equivalent_min_week
     *
     * activity_feature_contract `app_aligned_leisure_feature.knhanes_formula`
     * ```
     * leisure_moderate_min_week + 2 * leisure_vigorous_min_week
     * ```
     *
     * 앱이 강도별 주당 시간을 직접 받으므로 **KNHANES 공식을 그대로 쓸 수 있다.**
     * (계약서가 `app_formula_by_intensity` 로 제시한 일수×1회시간×단일강도 방식은
     *  `limitations` 에 "혼합 강도를 단순화한다" 고 적혀 있는 근사식이다.
     *  피그마 방식이 그 한계를 없앤다 — 강호님 확인 필요 항목으로 문서에 남겼다.)
     *
     * ⚠️ 이 값은 MET-분이 **아니다**. `moderate_equivalent_minutes_are_not_MET_minutes: true`
     * ⚠️ 저강도(산책·스트레칭)는 공식에 들어가지 않는다.
     */
    val leisureAerobicModerateEquivalentMinWeek: Double?
        get() {
            val moderate = leisureModerateMinWeek ?: return null
            val vigorous = leisureVigorousMinWeek ?: return null
            if (moderate < 0 || vigorous < 0) return null
            return moderate + 2.0 * vigorous
        }

    /** 활동 피처가 다 채워졌으면 W2, 아니면 W0 로 내려간다. */
    val tier: WaistInputTier
        get() = if (leisureAerobicModerateEquivalentMinWeek != null && strengthDaysWeek != null) {
            WaistInputTier.W2
        } else {
            WaistInputTier.W0
        }

    val bmi: Double get() = weightKg / ((heightCm / 100.0) * (heightCm / 100.0))

    /**
     * 모델에 넘길 수 있는 입력인지. UnsupportedPopulation 판정에 쓴다.
     * - 만 19세 미만: 계약 대상 인구(19세 이상)가 아님
     * - 임신 중: `exclude_from_all_p0_models`
     */
    fun unsupportedReason(): String? = when {
        ageYears < 19 -> "만 19세 이상을 기준으로 만든 참고 정보라, 지금은 보여드릴 수 없어요."
        pregnancyStatus == PregnancyStatus.PREGNANT ->
            "임신 중에는 일반 성인 기준 참고 정보를 보여드리지 않아요. 챌린지는 그대로 이용하실 수 있어요."
        else -> null
    }
}


/** 허리둘레 값의 출처. `waist_serving_policy.source_field: waist_source` */
enum class WaistSource {
    /** 사용자가 줄자로 직접 잰 값 */
    MEASURED,

    /** 모델이 추정한 값 */
    ESTIMATED,
}

/**
 * 추정 결과.
 *
 * `waist_serving_policy` 가 요구하는 기록 필드를 전부 담는다.
 *   source_field / estimator_version_field / input_snapshot_field
 */
data class WaistEstimate(
    val waistCm: Double,
    /** 화면에는 범위를 같이 보여준다. 점 추정만 단독 표시하지 않는다. */
    val lowCm: Double,
    val highCm: Double,
    /** 실제로 어떤 tier 로 계산했는지 */
    val tier: WaistInputTier,
    /** waist_estimator_version 에 기록될 값 */
    val estimatorVersion: String,
    /** input_snapshot_id — 어떤 입력으로 뽑은 값인지 되짚기 위한 키 */
    val inputSnapshotId: String,
) {
    val source: WaistSource get() = WaistSource.ESTIMATED
    val displayCm: Int get() = waistCm.roundToInt()
}

/**
 * ★ 이 앱은 **잰 허리둘레를 아예 받지 않는다.** (2026-08-27 팀 결정)
 *
 * 계약서는 두 출처를 모두 허용하지만(`waist_serving_policy.accepted_sources: [measured, estimated]`),
 * 동시에 이렇게 못박고 있다.
 *   protocol_v0_2 `features.estimated_waist_cm`
 *     raw_app_input: false
 *     measured_waist_as_disease_input: prohibited
 *
 * 즉 사용자가 잰 값을 넣어도 모델 입력으로는 절대 쓸 수 없다.
 * 그런데 화면에 잰 값을 보여주면, 참고 정보가 그 값으로 계산됐다고 오해하기 쉽다.
 * 두 값을 같이 두는 순간 "표시용 / 모델용" 을 헷갈려 섞는 사고가 언제든 난다.
 *
 * 그래서 입력 자체를 없앴다. 허리둘레는 **항상 모델이 추정한 값 하나뿐**이다.
 * 줄자 값을 다시 받으려면 계약의 serving bounds 와 표시 규칙이 확정된 뒤에
 * `waist_source` 를 화면에 함께 노출하는 설계로 다시 논의할 것.
 */

interface WaistEstimator {
    val info: ModelInfo

    /** 이 구현이 지원하는 tier. 동결 기록 기준 주 후보는 W2, 보존 후보가 W0. */
    val supportedTiers: Set<WaistInputTier>

    suspend fun estimate(input: WaistInput): ModelResult<WaistEstimate>
}
