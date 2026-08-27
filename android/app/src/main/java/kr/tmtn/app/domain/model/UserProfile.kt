package kr.tmtn.app.domain.model

import kr.tmtn.app.domain.ml.Sex

/**
 * 온보딩에서 받는 값 전부.
 *
 * 수집하지 않는 것 (프로젝트 지침 · README 제품 원칙):
 *  - 주민등록번호, 전체 생년월일 (출생연도만 받는다)
 *  - 원시 정밀 위치
 *  - 혈당·당화혈색소·실제 혈압 — 앱 입력으로 받지 않는다
 */
data class UserProfile(
    val nickname: String = "",
    val birthYear: Int = 0,
    val sex: Sex? = null,
    val heightCm: Double = 0.0,
    val weightKg: Double = 0.0,
    /** 사용자가 줄자로 잰 값. null 이면 모델 1이 추정한다. */
    val waistCm: Double? = null,
    val aerobicMinutesPerWeek: Int = 0,
    val strengthDaysPerWeek: Int = 0,
    val goal: String = "",
    val preferredSlot: String = "언제나",
) {
    val isComplete: Boolean
        get() = birthYear > 1900 && heightCm > 0 && weightKg > 0
}
