package kr.tmtn.app.domain.ml.placeholder

import kr.tmtn.app.domain.ml.ModelInfo
import kr.tmtn.app.domain.ml.ModelResult
import kr.tmtn.app.domain.ml.SexCode
import kr.tmtn.app.domain.ml.WaistEstimate
import kr.tmtn.app.domain.ml.WaistEstimator
import kr.tmtn.app.domain.ml.WaistInput
import kr.tmtn.app.domain.ml.WaistInputTier
import kotlin.math.abs

/**
 * 모델 ① 자리를 채워 두는 임시 구현.
 *
 * ⚠️ 학습된 모델이 아니다. 화면 흐름 확인용 계산식이며 임상적 근거가 없다.
 *   `isPlaceholder = true` 라서 화면에 "샘플 값" 안내가 뜬다. 이대로 배포하면 안 된다.
 *
 * 진짜 모델(강호님 nested cross-fitted 서브모델)로 교체할 때:
 *   1. 동결 기록 기준은 tier **W2** · random_forest_regressor
 *      (n_estimators=500, max_depth=12, min_samples_leaf=5, max_features=0.7, random_state=42)
 *   2. 피처 순서는 `WaistInputTier.features` 그대로 — **순서가 바뀌면 에러 없이 값만 틀린다**
 *   3. `estimatorVersion` 에 실제 버전을 넣는다 (기록 필드 waist_estimator_version)
 *   4. ModelRegistry 한 줄만 바꾸면 화면은 그대로 동작한다
 */
class PlaceholderWaistEstimator : WaistEstimator {

    override val info = ModelInfo(
        name = "waist-placeholder",
        version = "0.0.2",
        isPlaceholder = true,
        note = "학습 모델 전 임시 계산식입니다. 실제 추정값이 아닙니다.",
    )

    /** 임시 구현이라 두 tier 를 다 흉내낸다. 실모델은 동결 기록대로 W2 가 주 후보. */
    override val supportedTiers = setOf(WaistInputTier.W0, WaistInputTier.W2)

    override suspend fun estimate(input: WaistInput): ModelResult<WaistEstimate> {
        input.unsupportedReason()?.let { return ModelResult.UnsupportedPopulation(it) }

        if (input.heightCm <= 0 || input.weightKg <= 0) {
            return ModelResult.NotReady("키와 몸무게가 있어야 계산할 수 있어요.")
        }
        val sex = input.sexCode
            ?: return ModelResult.NotReady("성별을 넣어 주시면 허리둘레를 추정할 수 있어요.")

        val bmi = input.bmi
        val age = input.ageYears

        // ⚠️ 아래 계수에는 아무 근거가 없다. 자리만 잡아 두는 값이다.
        var waist = when (sex) {
            SexCode.MALE -> 22.0 + bmi * 2.30 + age * 0.10
            SexCode.FEMALE -> 18.0 + bmi * 2.25 + age * 0.12
        }

        // W2 일 때만 활동량을 반영한다 (실모델도 tier 에 따라 피처 수가 다르다)
        val tier = input.tier
        if (tier == WaistInputTier.W2) {
            val aerobic = input.leisureAerobicModerateEquivalentMinWeek ?: 0.0
            val strength = input.strengthDaysWeek ?: 0
            waist -= (aerobic.coerceAtMost(600.0) * 0.004)
            waist -= (strength.coerceAtMost(5) * 0.35)
        }

        waist = waist.coerceIn(50.0, 150.0)

        // 동결 기록의 release gate 는 MAE 4.0cm 이하다. 임시값의 범위는 그보다 넉넉하게 잡는다.
        val halfWidth = if (tier == WaistInputTier.W2) 4.0 else 5.0

        return ModelResult.Ready(
            WaistEstimate(
                waistCm = waist,
                lowCm = waist - halfWidth,
                highCm = waist + halfWidth,
                tier = tier,
                estimatorVersion = info.versionTag,
                inputSnapshotId = snapshotId(input),
            ),
            info,
        )
    }

    /**
     * input_snapshot_id — 어떤 입력으로 뽑은 값인지 되짚기 위한 키.
     * 같은 입력이면 같은 값이 나와야 한다(재현성 확인용).
     */
    private fun snapshotId(i: WaistInput): String {
        val parts = listOf(
            i.ageYears, i.sexCode?.knhanesCode, i.heightCm, i.weightKg,
            i.leisureAerobicModerateEquivalentMinWeek, i.strengthDaysWeek, i.tier.name,
        )
        return "snap-" + abs(parts.joinToString("|").hashCode()).toString(16)
    }
}
