package kr.tmtn.app.domain.ml.placeholder

import kr.tmtn.app.domain.ml.ModelInfo
import kr.tmtn.app.domain.ml.ModelResult
import kr.tmtn.app.domain.ml.Sex
import kr.tmtn.app.domain.ml.WaistEstimate
import kr.tmtn.app.domain.ml.WaistEstimator
import kr.tmtn.app.domain.ml.WaistInput

/**
 * 모델 1 자리를 채워 두는 임시 구현.
 *
 * ⚠ 이것은 학습된 모델이 아니다. 화면이 도는지 보려고 넣은 계산식이다.
 *   임상적으로 검증된 값이 아니며, 그대로 배포하면 안 된다.
 *   isPlaceholder = true 이므로 화면에 "샘플 값" 안내가 뜬다.
 *
 * 교체하는 사람에게: 이 파일을 지우고 WaistEstimator 를 구현한 클래스를 만든 뒤
 * ModelRegistry.waistEstimator 만 바꾸면 된다.
 */
class PlaceholderWaistEstimator : WaistEstimator {

    override val info = ModelInfo(
        name = "waist-placeholder",
        version = "0.0.1",
        isPlaceholder = true,
        note = "학습 모델 전 임시 계산식입니다. 실제 추정값이 아닙니다.",
    )

    override suspend fun estimate(input: WaistInput): ModelResult<WaistEstimate> {
        if (input.heightCm <= 0 || input.weightKg <= 0) {
            return ModelResult.NotReady("키와 몸무게가 있어야 계산할 수 있어요.")
        }
        val bmi = input.bmi
        val age = 2026 - input.birthYear
        // 아래 계수에는 근거가 없다. 자리만 잡아 두는 값이다.
        val base = when (input.sex) {
            Sex.MALE -> 22.0 + bmi * 2.30 + age * 0.10
            Sex.FEMALE -> 18.0 + bmi * 2.25 + age * 0.12
            null -> 20.0 + bmi * 2.28 + age * 0.11
        }
        val waist = base.coerceIn(50.0, 150.0)
        return ModelResult.Ready(
            WaistEstimate(waistCm = waist, lowCm = waist - 4.0, highCm = waist + 4.0),
            info,
        )
    }
}
