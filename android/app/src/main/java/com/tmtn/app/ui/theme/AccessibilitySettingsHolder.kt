package com.tmtn.app.ui.theme

import androidx.compose.runtime.mutableStateOf

/**
 * ⚠️ 2026-09-04 QA(P0-6) 반영: "내 정보 > 접근성"에서 글자 크기·고대비를 켜도 서버에는
 * 저장이 되는데(ProfileState.updateAccessibility), 그 값을 실제로 화면에 반영하는 코드가
 * 어디에도 없었음. 설정 화면 자체의 "미리보기" 카드조차 고정 크기였음.
 *
 * TmtnType/TMTNv1Theme이 이 홀더를 구독해서 전역으로 반영함. MainActivity가 앱 시작 시
 * 한 번 불러오고, ProfileState.updateAccessibility()가 성공할 때마다 바로 갱신해서
 * (내 정보 탭을 벗어나도) 설정 화면을 나가는 즉시 다른 화면에도 바로 적용되게 함.
 *
 * large_controls(버튼 최소 터치 영역)·reduced_motion(애니메이션 감소)은 이번엔 반영 안 함 -
 * 글자 크기·고대비만 우선 처리.
 */
object AccessibilitySettingsHolder {
    /** "NORMAL" / "LARGE" / "EXTRA_LARGE" */
    var textScaleHint = mutableStateOf("NORMAL")
    var seniorMode = mutableStateOf(false)

    fun apply(largeControls: Boolean, seniorModeValue: Boolean, textScaleHintValue: String?) {
        seniorMode.value = seniorModeValue
        textScaleHint.value = textScaleHintValue ?: "NORMAL"
    }
}

fun textScaleHintToFactor(hint: String): Float = when (hint) {
    "LARGE" -> 1.15f
    "EXTRA_LARGE" -> 1.3f
    else -> 1f
}
