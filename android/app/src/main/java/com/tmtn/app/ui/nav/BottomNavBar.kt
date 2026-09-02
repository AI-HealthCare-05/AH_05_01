package com.tmtn.app.ui.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** Figma 하단 최상위 목적지 5개(§1): 기록·댐·홈·참고·마이 (홈이 가운데).
 * ⚠️ Figma 컴포넌트 설명엔 "카드"라고 되어 있지만 실제 배치는 전부 "댐" — 라우팅은 댐 기준. */
/** Figma FLOWS.md(2026-08-31 갱신): "홈·기록·틈튼지수·댐·내 정보" 순서, 참고→틈튼지수·마이→내 정보로 이름 변경됨.
 * 진입 화면: 홈→B01, 기록→D01, 틈튼지수→E01, 댐→G01, 내 정보→F01 */
enum class MainTab(val label: String) {
    HOME("홈"),
    RECORD("기록"),
    REFERENCE("틈튼지수"),
    DAM("댐"),
    MY("내 정보"),
}

/** Figma base.css .nav-bar: 높이 104(항목 80 + 제스처 inset 24), 항목 폭 78. */
@Composable
fun BottomNavBar(currentTab: MainTab, onTabSelected: (MainTab) -> Unit) {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.navigationContainer)
            .border(width = 1.dp, color = colors.outlineVariant),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.Center) {
                MainTab.entries.forEach { tab ->
                    NavItem(
                        tab = tab,
                        isActive = tab == currentTab,
                        onClick = { onTabSelected(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(tab: MainTab, isActive: Boolean, onClick: () -> Unit) {
    val colors = LocalTmtnColors.current
    val iconColor = if (isActive) colors.navigationIconActive else colors.navigationIconInactive

    Column(
        modifier = Modifier
            .width(78.dp)
            .height(80.dp)
            .clickable { onClick() }
            .padding(top = 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .background(
                    if (isActive) colors.navigationIndicator else Color.Transparent,
                    RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            TabIcon(tab = tab, color = iconColor, strokeWidth = if (isActive) 2.2f else 1.6f)
        }
        Text(
            tab.label,
            style = TmtnType.label.copy(fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium),
            color = if (isActive) colors.navigationLabelActive else colors.navigationLabelInactive,
        )
    }
}

/**
 * 실제 팀장님이 전달한 Navigation bar.png 참고 이미지를 그대로 벡터로 옮긴 아이콘들.
 * material-icons-extended 의존성이 프로젝트에 없어서(build.gradle.kts 확인함),
 * Material Icons 대신 Canvas로 직접 그려서 의존성 추가 없이 안전하게 구현함.
 */
@Composable
private fun TabIcon(tab: MainTab, color: Color, strokeWidth: Float) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = strokeWidth.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (tab) {
            MainTab.HOME -> {
                // 집 모양 - 지붕(삼각형)과 몸통(사각형)이 이어진 오각형 윤곽선
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.08f)
                    lineTo(w * 0.9f, h * 0.42f)
                    lineTo(w * 0.9f, h * 0.92f)
                    lineTo(w * 0.1f, h * 0.92f)
                    lineTo(w * 0.1f, h * 0.42f)
                    close()
                }
                drawPath(path, color = color, style = stroke)
            }
            MainTab.RECORD -> {
                // 달력 + 체크 표시
                val left = w * 0.15f
                val right = w * 0.85f
                val top = h * 0.22f
                val bottom = h * 0.88f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(w * 0.08f),
                    style = stroke,
                )
                // 위쪽 고리 두 개
                drawLine(color, Offset(w * 0.32f, h * 0.08f), Offset(w * 0.32f, top), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.68f, h * 0.08f), Offset(w * 0.68f, top), stroke.width, StrokeCap.Round)
                // 체크 표시
                val checkPath = Path().apply {
                    moveTo(w * 0.32f, h * 0.58f)
                    lineTo(w * 0.46f, h * 0.72f)
                    lineTo(w * 0.70f, h * 0.42f)
                }
                drawPath(checkPath, color = color, style = stroke)
            }
            MainTab.REFERENCE -> {
                // 막대그래프 (오름차순 3개 막대 + 바닥선)
                val baseline = h * 0.88f
                drawLine(color, Offset(w * 0.08f, baseline), Offset(w * 0.92f, baseline), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.25f, h * 0.68f), Offset(w * 0.25f, baseline), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, h * 0.48f), Offset(w * 0.5f, baseline), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.75f, h * 0.22f), Offset(w * 0.75f, baseline), stroke.width, StrokeCap.Round)
            }
            MainTab.DAM -> {
                // 댐 구조 - 위쪽 가로대 + 기둥 3개 + 아래쪽 물결선
                val top = h * 0.22f
                drawLine(color, Offset(w * 0.1f, top), Offset(w * 0.9f, top), stroke.width, StrokeCap.Round)
                val pillarBottom = h * 0.62f
                listOf(0.25f, 0.5f, 0.75f).forEach { fx ->
                    drawLine(color, Offset(w * fx, top), Offset(w * fx, pillarBottom), stroke.width, StrokeCap.Round)
                }
                drawLine(color, Offset(w * 0.1f, pillarBottom), Offset(w * 0.9f, pillarBottom), stroke.width, StrokeCap.Round)
                // 물결선
                val wavePath = Path().apply {
                    moveTo(w * 0.08f, h * 0.8f)
                    quadraticBezierTo(w * 0.25f, h * 0.68f, w * 0.42f, h * 0.8f)
                    quadraticBezierTo(w * 0.58f, h * 0.92f, w * 0.75f, h * 0.8f)
                    quadraticBezierTo(w * 0.85f, h * 0.72f, w * 0.92f, h * 0.8f)
                }
                drawPath(wavePath, color = color, style = stroke)
            }
            MainTab.MY -> {
                // 사람 - 머리(원) + 몸통(아치)
                val headRadius = w * 0.16f
                drawCircle(color = color, radius = headRadius, center = Offset(w * 0.5f, h * 0.3f), style = stroke)
                val bodyPath = Path().apply {
                    moveTo(w * 0.18f, h * 0.9f)
                    quadraticBezierTo(w * 0.18f, h * 0.55f, w * 0.5f, h * 0.55f)
                    quadraticBezierTo(w * 0.82f, h * 0.55f, w * 0.82f, h * 0.9f)
                }
                drawPath(bodyPath, color = color, style = stroke)
            }
        }
    }
}
