package kr.tmtn.app.domain.ml.placeholder

import kr.tmtn.app.domain.ml.IndexBand
import kr.tmtn.app.domain.ml.IndexFactor
import kr.tmtn.app.domain.ml.ModelInfo
import kr.tmtn.app.domain.ml.ModelResult
import kr.tmtn.app.domain.ml.Sex
import kr.tmtn.app.domain.ml.TmtnIndexInput
import kr.tmtn.app.domain.ml.TmtnIndexResult
import kr.tmtn.app.domain.ml.TmtnIndexScorer
import kotlin.math.roundToInt

/**
 * 모델 3 자리를 채워 두는 임시 구현.
 *
 * ⚠ KNHANES 로 학습한 모델이 아니다. 화면 흐름 확인용 규칙 점수다.
 *   숫자가 그럴듯해 보여도 어떤 의학적 의미도 없다.
 *
 * 교체하는 사람에게:
 *  - 입력은 TmtnIndexInput 그대로 쓰면 된다. 화면은 안 고쳐도 된다.
 *  - factors 에 SHAP 같은 기여도를 채워 주면 "예측 근거" 화면이 자동으로 채워진다.
 *  - disclaimer 는 비워 두지 말 것.
 */
class PlaceholderTmtnIndexScorer : TmtnIndexScorer {

    override val info = ModelInfo(
        name = "tmtn-index-placeholder",
        version = "0.0.1",
        isPlaceholder = true,
        note = "KNHANES 학습 모델 전 임시 규칙 점수입니다.",
    )

    override suspend fun score(input: TmtnIndexInput): ModelResult<TmtnIndexResult> {
        if (input.heightCm <= 0 || input.weightKg <= 0) {
            return ModelResult.NotReady("키와 몸무게를 입력하면 계산할 수 있어요.")
        }

        val bmi = input.weightKg / ((input.heightCm / 100.0) * (input.heightCm / 100.0))
        val age = 2026 - input.birthYear
        val factors = mutableListOf<IndexFactor>()

        var raw = 50.0

        val bmiPart = ((bmi - 22.0) * 2.4).coerceIn(-12.0, 22.0)
        raw += bmiPart
        factors += IndexFactor("체질량지수", bmiPart / 22.0, "키와 몸무게로 계산한 값이에요.")

        val waist = input.waistCm
        if (waist != null) {
            val ref = if (input.sex == Sex.FEMALE) 85.0 else 90.0
            val waistPart = ((waist - ref) * 0.55).coerceIn(-10.0, 20.0)
            raw += waistPart
            val where = if (input.waistFromModel) "추정한 허리둘레" else "직접 입력한 허리둘레"
            factors += IndexFactor("허리둘레", waistPart / 20.0, "$where 기준이에요.")
        }

        val agePart = ((age - 35) * 0.28).coerceIn(-6.0, 14.0)
        raw += agePart
        factors += IndexFactor("나이대", agePart / 14.0, "나이대별 분포를 함께 봐요.")

        val aerobicPart = -(input.aerobicMinutesPerWeek.coerceAtMost(300) * 0.035)
        raw += aerobicPart
        factors += IndexFactor("주간 유산소", aerobicPart / 10.5, "일주일에 움직인 시간이에요.")

        val strengthPart = -(input.strengthDaysPerWeek.coerceAtMost(5) * 1.4)
        raw += strengthPart
        factors += IndexFactor("주간 근력", strengthPart / 7.0, "일주일에 근력 운동을 한 날이에요.")

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
                factors = factors.sortedByDescending { kotlin.math.abs(it.contribution) },
                windowLabel = "최근 7일 입력 기준",
            ),
            info,
        )
    }
}
