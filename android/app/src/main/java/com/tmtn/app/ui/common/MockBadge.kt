package com.tmtn.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** ⚠️ 2026-09-04 PR #12 리뷰(P0) 반영: 틈튼지수 관련 화면 중 "참고" 탭 E01(요약)에만
 * 이 뱃지가 있고, 홈 화면 요약 카드·E03(영역별 점수)에는 없어서 상수 Mock 값(74/78/76)이
 * 진짜 점수처럼 보이고 있었음. 당뇨·고혈압처럼 실제 질환과 관련된 화면에서 이건 위험한
 * 문제라, 값이 나오는 모든 화면에서 이 뱃지 하나로 통일해서 재사용함.
 *
 * ⚠️ 문구("Mock")는 임시 - 리뷰어가 시니어 사용자 기준 한글 문구를 따로 전달하기로 함.
 * 그 문구가 오면 여기 한 곳만 바꾸면 전체에 반영됨.
 */
@Composable
fun MockBadge() {
    val colors = LocalTmtnColors.current
    Text(
        "Mock", style = TmtnType.caption, color = colors.onSurfaceVariant,
        modifier = Modifier.background(colors.disabledContainer, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
