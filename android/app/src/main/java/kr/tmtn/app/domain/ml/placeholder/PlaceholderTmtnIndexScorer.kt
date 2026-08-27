package kr.tmtn.app.domain.ml.placeholder

import kr.tmtn.app.domain.ml.IndexBand
import kr.tmtn.app.domain.ml.IndexFactor
import kr.tmtn.app.domain.ml.ModelInfo
import kr.tmtn.app.domain.ml.ModelResult
import kr.tmtn.app.domain.ml.PregnancyStatus
import kr.tmtn.app.domain.ml.SexCode
import kr.tmtn.app.domain.ml.TmtnIndexInput
import kr.tmtn.app.domain.ml.TmtnIndexResult
import kr.tmtn.app.domain.ml.TmtnIndexScorer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 모델 ③ 자리를 채워 두는 임시 구현.
 *
 * ⚠️ 틈튼지수는 AI 팀 계약(protocol_v0_2)에서 **범위 밖**이다.
 *   `tmtn_index.included: false / reason: separately_validated_composite_formula_required`
 *   즉 강호님이 만들어 주지 않는다. 팀이 따로 정의하고 검증해야 하는 합성 지표다.
 *   그때까지 이 파일은 화면 흐름 확인용 규칙 점수로 남는다. 숫자에 의학적 의미가 없다.
 */
class PlaceholderTmtnIndexScorer : TmtnIndexScorer {

    override val info = ModelInfo(
        name = "tmtn-index-placeholder",
        version = "0.0.2",
        isPlaceholder = true,
        note = "합성 공식이 아직 정의되지 않아 임시 규칙 점수를 쓰고 있어요.",
    )

    override suspend fun score(input: TmtnIndexInput): ModelResult<TmtnIndexResult> {
        if (input.ageYears < 19) {
            return ModelResult.UnsupportedPopulation(
                "만 19세 이상을 기준으로 만든 참고 정보라, 지금은 보여드릴 수 없어요.",
            )
        }
        if (input.pregnancyStatus == PregnancyStatus.PREGNANT) {
            return ModelResult.UnsupportedPopulation(
                "임신 중에는 일반 성인 기준 참고 정보를 보여드리지 않아요. 챌린지는 그대로 이용하실 수 있어요.",
            )
        }
        if (input.heightCm <= 0 || input.weightKg <= 0) {
            return ModelResult.NotReady("키와 몸무게를 입력하면 계산할 수 있어요.")
        }

        val bmi = input.weightKg / ((input.heightCm / 100.0) * (input.heightCm / 100.0))
        val factors = mutableListOf<IndexFactor>()
        var raw = 50.0

        val bmiPart = ((bmi - 22.0) * 2.4).coerceIn(-12.0, 22.0)
        raw += bmiPart
        factors += IndexFactor("체질량지수", bmiPart / 22.0, "키와 몸무게로 계산한 값이에요.")

        // ★ 잰 허리둘레는 여기 들어오지 않는다. 모델 추정값만 받는다.
        val waist = input.estimatedWaistCm
        if (waist != null) {
            val ref = if (input.sexCode == SexCode.FEMALE) 85.0 else 90.0
            val waistPart = ((waist - ref) * 0.55).coerceIn(-10.0, 20.0)
            raw += waistPart
            factors += IndexFactor("허리둘레(추정)", waistPart / 20.0, "키·몸무게·활동량으로 추정한 값을 썼어요.")
        }

        val agePart = ((input.ageYears - 35) * 0.28).coerceIn(-6.0, 14.0)
        raw += agePart
        factors += IndexFactor("나이대", agePart / 14.0, "나이대별 분포를 함께 봐요.")

        val aerobic = input.leisureAerobicModerateEquivalentMinWeek
        if (aerobic != null) {
            val part = -(aerobic.coerceAtMost(600.0) * 0.018)
            raw += part
            factors += IndexFactor("주간 유산소", part / 10.8, "여가 시간에 움직인 양이에요.")
        }

        val strength = input.strengthDaysWeek
        if (strength != null) {
            val part = -(strength.coerceAtMost(5) * 1.4)
            raw += part
            factors += IndexFactor("주간 근력", part / 7.0, "일주일에 근력 운동을 한 날이에요.")
        }

        val score = raw.roundToInt().coerceIn(0, 100)
        val band = when {
            score < 45 -> IndexBand.LOW
            score < 70 -> IndexBand.NORMAL
            else -> IndexBand.WATCH
        }

        return ModelResult.Ready(
            TmtnIndexResult(
                score = score,
                band = band,
                factors = factors.sortedByDescending { abs(it.contribution) },
                windowLabel = "최근 입력 기준",
                usedEstimatedWaist = waist != null,
            ),
            info,
        )
    }
}
