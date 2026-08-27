package kr.tmtn.app.domain.ml

/**
 * 모델 1 — 허리둘레 추정.
 *
 * 사용자가 줄자로 잰 값을 안 넣었거나 못 넣었을 때, 키·몸무게 등으로 허리둘레를 추정한다.
 * 추정값은 모델 3(틈튼지수)의 입력으로도 그대로 들어간다.
 *
 * 지켜야 할 것:
 *  - 사용자가 직접 입력한 허리둘레가 있으면 **그 값이 항상 우선**이다. 모델로 덮어쓰지 않는다.
 *  - 화면에는 추정값임을 반드시 표시한다. 잰 값과 추정값을 같은 모양으로 보여주지 않는다.
 *  - 이 값은 진단이 아니다. 체형 평가·외모 관련 표현을 붙이지 않는다.
 */
data class WaistInput(
    val birthYear: Int,
    val sex: Sex?,
    val heightCm: Double,
    val weightKg: Double,
) {
    val bmi: Double get() = weightKg / ((heightCm / 100.0) * (heightCm / 100.0))
}

data class WaistEstimate(
    val waistCm: Double,
    /** 신뢰구간. 화면에는 "약 82cm (78~86)" 처럼 범위를 같이 보여준다. */
    val lowCm: Double,
    val highCm: Double,
)

interface WaistEstimator {
    val info: ModelInfo
    suspend fun estimate(input: WaistInput): ModelResult<WaistEstimate>
}
