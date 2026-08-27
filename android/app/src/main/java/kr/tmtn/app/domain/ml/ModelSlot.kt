package kr.tmtn.app.domain.ml

/**
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  모델을 넣는 사람에게                                             │
 * │                                                                  │
 * │  이 패키지가 "모델 자리" 입니다. 화면 코드는 여기 인터페이스만    │
 * │  알고 있고, 실제 구현이 무엇인지 모릅니다.                        │
 * │                                                                  │
 * │  넣는 방법:                                                      │
 * │   1) 인터페이스를 구현한 클래스를 만든다                          │
 * │   2) ModelRegistry 에서 Placeholder 대신 그 클래스를 반환한다     │
 * │   3) info.isPlaceholder = false 로 둔다                          │
 * │                                                                  │
 * │  화면은 절대 고치지 않아도 됩니다.                                │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * 이 파일의 타입 이름과 값은 AI 팀 계약서를 그대로 따릅니다.
 *   tmtn_ai/contracts/data_contract_v0_3.yaml
 *   tmtn_ai/contracts/model_experiment_protocol_v0_2.yaml
 *   tmtn_ai/contracts/activity_feature_contract_v0_1.yaml
 * 계약서가 바뀌면 여기부터 맞춥니다. 반대로 앱 편의를 위해 계약을 바꾸지 않습니다.
 */

/** 지금 붙어 있는 모델이 무엇인지 화면과 기록에 정직하게 드러내기 위한 값. */
data class ModelInfo(
    /** 계약서의 estimator_version 필드에 그대로 들어갈 값 */
    val name: String,
    val version: String,
    /** true = 아직 진짜 모델이 아님. 화면에 "샘플" 안내가 뜬다. */
    val isPlaceholder: Boolean,
    val note: String = "",
) {
    /** waist_estimator_version 등 기록용 문자열 */
    val versionTag: String get() = "$name@$version"
}

/** 모델 호출 결과. "값이 없음"·"대상이 아님"·"실패" 를 서로 다른 상태로 다룬다. */
sealed interface ModelResult<out T> {
    data class Ready<T>(val value: T, val info: ModelInfo) : ModelResult<T>

    /** 입력이 모자라 계산하지 않은 상태. 오류가 아니다. */
    data class NotReady(val reason: String) : ModelResult<Nothing>

    /**
     * 모델이 다루는 인구집단이 아니어서 점수를 내지 않는 상태.
     * data_contract_v0_3 `serving_rule: return_unsupported_population_without_general_adult_scores`
     * 임신 중이거나 만 19세 미만이면 여기로 온다. 절대 일반 성인 점수를 대신 보여주지 않는다.
     */
    data class UnsupportedPopulation(val reason: String) : ModelResult<Nothing>

    /** 모델은 있는데 실행이 실패한 상태. */
    data class Failed(val reason: String) : ModelResult<Nothing>
}

/**
 * 성별 코드. **KNHANES 원본 코드를 그대로 쓴다** (1=남성, 2=여성).
 * data_contract_v0_3 `app_raw_inputs.sex_code`
 *
 * 주의: 계약서 상태가 `knhanes_1_male_2_female_confirmed_product_policy_pending` 이다.
 * 두 값 외의 제품 정책(선택 안 함 등)은 아직 정해지지 않았으므로, 앱에서는 null 로 두고
 * 모델에 넘기지 않는다. sex_code 는 6개 모델 피처 중 하나라 없으면 추정하지 않는다.
 */
enum class SexCode(val knhanesCode: Int) {
    MALE(1),
    FEMALE(2),
    ;

    companion object {
        fun fromCode(code: Int?): SexCode? = entries.firstOrNull { it.knhanesCode == code }
    }
}

/**
 * 임신 여부. **모델 피처가 아니라 자격·안전 판단용이다.**
 * data_contract_v0_3 `eligibility_and_safety.pregnancy`
 *   model_cohort_policy: exclude_from_all_p0_models
 *   app_field_role: eligibility_safety_not_model_feature
 *   challenge_service_policy: separate_safe_path_not_blanket_exclusion
 *     → 건강 점수는 내지 않지만, 챌린지 서비스 자체를 막지는 않는다.
 */
enum class PregnancyStatus {
    NOT_PREGNANT,
    PREGNANT,
    /** 응답하지 않음. 0으로 바꾸거나 임의로 추정하지 않는다. */
    UNKNOWN,
}

/**
 * 유산소 강도 밴드 — **피그마 A08 화면 기준 3단계**.
 *
 * 계약서(`activity_feature_contract_v0_1`)의 `aerobic_typical_intensity` 는
 * moderate / vigorous 두 값뿐이고, 파생 피처 공식도 이 둘만 쓴다.
 *   leisure_aerobic_moderate_equivalent_min_week = moderate_min_week + 2 × vigorous_min_week
 *
 * 저강도(산책·스트레칭)는 그 공식에 들어가지 않는다.
 * 그래도 받는 이유는 사용자가 "나는 산책은 해요" 라고 답할 자리가 필요하고,
 * 챌린지를 고를 때 쓰기 때문이다. **모델 피처로는 넘기지 않는다.**
 */
enum class AerobicBand(val label: String, val hint: String, val isModelFeature: Boolean) {
    LIGHT("저강도", "숨이 차지 않아요 · 산책, 스트레칭", false),
    MODERATE("중강도", "숨이 조금 차요 · 빠르게 걷기, 자전거", true),
    VIGOROUS("고강도", "숨이 많이 차요 · 달리기, 등산", true),
}

/**
 * 근력 운동 강도 — 피그마 A08 화면에 있다.
 * ⚠️ **계약서에 없는 값이다.** 모델 피처는 `strength_days_week`(주 횟수) 하나뿐이다.
 * 챌린지 난이도를 고르는 데만 쓰고 모델에는 넘기지 않는다.
 */
enum class StrengthIntensity(val label: String, val hint: String) {
    LIGHT("가볍게", "15회+ 가능"),
    MODERATE("적당히", "10~12회면 힘듦"),
    HARD("힘들게", "8회면 한계"),
}
