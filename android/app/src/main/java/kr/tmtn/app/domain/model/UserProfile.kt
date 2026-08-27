package kr.tmtn.app.domain.model

import kr.tmtn.app.domain.ml.PregnancyStatus
import kr.tmtn.app.domain.ml.SexCode
import kr.tmtn.app.domain.ml.StrengthIntensity
import kr.tmtn.app.domain.ml.WaistInput
import java.time.LocalDate

/**
 * 온보딩에서 받는 값 전부. **피그마 A07(기본 정보) · A08(운동 정보) 기준.**
 *
 * A07 필수: 이름 · 닉네임(선택) · 생년월(연·월만) · 성별 · 키 · 몸무게
 * A08 필수: 근력 주 횟수 + 강도 · 유산소 강도별 주당 시간(저·중·고)
 *
 * 받지 않는 것
 *  - **허리둘레** — 모델이 추정한다. 잰 값은 모델 입력으로 쓸 수 없어서(계약 금지 항목)
 *    화면에 두면 오해만 생긴다. 피그마에도 없다.
 *  - 정확한 생년월일 — 연·월까지만
 *  - 주민등록번호 · 원시 정밀 위치 · 혈압/혈당 수치
 */
data class UserProfile(
    val name: String = "",
    /** 비우면 이름을 그대로 쓴다 */
    val nickname: String = "",

    /** 연·월만 받는다 */
    val birthYear: Int = 0,
    val birthMonth: Int = 0,

    /** sex_code — KNHANES 1=남 2=여. **필수**. 6개 모델 피처 중 하나 */
    val sexCode: SexCode? = null,

    val heightCm: Double = 0.0,
    val weightKg: Double = 0.0,

    /* ── 근력운동 (피그마 A08) ─────────────────────────── */

    /** 주 횟수 0~5. 5 = "주 5회 이상". 계약서 `strength_days_week` 로 그대로 간다 */
    val strengthDaysWeek: Int? = null,

    /** 강도 — ⚠️ 계약에 없는 값. 챌린지 난이도용이고 모델에는 안 넘긴다 */
    val strengthIntensity: StrengthIntensity? = null,

    /* ── 유산소운동 · 강도별 주당 분 (피그마 A08) ───────── */

    /** 저강도 — 모델 공식에 들어가지 않는다. 챌린지 참고용 */
    val aerobicLightMinWeek: Int? = null,

    /** 중강도 — 공식의 moderate */
    val aerobicModerateMinWeek: Int? = null,

    /** 고강도 — 공식에서 2배로 셈 */
    val aerobicVigorousMinWeek: Int? = null,

    /** 모델 피처가 아니다. 자격·안전 판단용 */
    val pregnancyStatus: PregnancyStatus = PregnancyStatus.UNKNOWN,
) {
    /** 화면에 부를 이름. 닉네임이 있으면 그걸 쓴다 */
    val displayName: String get() = nickname.ifBlank { name }.ifBlank { "친구" }

    /** 만 나이. 연·월까지만 알므로 생일 전후 하루 이틀은 어긋날 수 있다 */
    val ageYears: Int
        get() {
            if (birthYear <= 1900) return 0
            val today = LocalDate.now()
            var age = today.year - birthYear
            if (birthMonth in 1..12 && today.monthValue < birthMonth) age -= 1
            return age
        }

    /** A07 단계를 넘어갈 수 있는지 */
    val basicComplete: Boolean
        get() = birthYear > 1900 && birthMonth in 1..12 && sexCode != null && heightCm > 0 && weightKg > 0

    /** A08 단계까지 다 채웠는지. 피그마: "세 가지 강도를 모두 채워 주세요" */
    val exerciseComplete: Boolean
        get() = strengthDaysWeek != null && strengthIntensity != null &&
            aerobicLightMinWeek != null && aerobicModerateMinWeek != null && aerobicVigorousMinWeek != null

    val isComplete: Boolean get() = basicComplete && exerciseComplete

    /** 계약서 필드 이름으로 모델 입력을 만든다. 이 변환은 여기 한 곳에서만 한다. */
    fun toWaistInput(): WaistInput? {
        if (!basicComplete) return null
        return WaistInput(
            ageYears = ageYears,
            sexCode = sexCode,
            heightCm = heightCm,
            weightKg = weightKg,
            // 저강도는 넘기지 않는다 — KNHANES 공식에 들어가지 않는다
            leisureModerateMinWeek = aerobicModerateMinWeek,
            leisureVigorousMinWeek = aerobicVigorousMinWeek,
            strengthDaysWeek = strengthDaysWeek,
            strengthTopCoded = strengthDaysWeek == 5,
            pregnancyStatus = pregnancyStatus,
        )
    }
}
