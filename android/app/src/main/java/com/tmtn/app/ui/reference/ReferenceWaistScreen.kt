package com.tmtn.app.ui.reference

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.common.TmtnMascot
import com.tmtn.app.ui.onboarding.TmtnOutlinedButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun WaistEstimateSummary(result: WaistEstimateUi, onOpen: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxWidth().tmtnClickable(onClick = onOpen).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("허리둘레", style = TmtnType.title, color = colors.onSurface, modifier = Modifier.weight(1f))
            ScoreArrow()
        }
        Text(when (result) {
            is WaistEstimateUi.Available -> "약 ${result.displayValue} cm · 모델 추정"
            WaistEstimateUi.Loading -> "추정값을 불러오고 있어요"
            WaistEstimateUi.Unavailable -> "아직 확인할 추정값이 없어요"
            WaistEstimateUi.Failed -> "추정값 다시 확인하기"
        }, style = TmtnType.bodyLarge, color = colors.onSurfaceVariant)
    }
}

/** A cm result is deliberately separate from the score bands and peer-position illustration. */
@Composable
internal fun WaistEstimateArticle(result: WaistEstimateUi, onRetry: () -> Unit = {}) {
    val colors = LocalTmtnColors.current
    val large = LocalTmtnTextScale.current * LocalDensity.current.fontScale > 1.35f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("내 몸의 작은 단서", style = TmtnType.body, color = colors.onSurfaceVariant)
        Text("추정 허리둘레", style = TmtnType.title, color = colors.onSurface)
        when (result) {
            is WaistEstimateUi.Available -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = "모델이 추정한 허리둘레 약 ${result.displayValue} 센티미터"
                    }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("약", style = TmtnType.body, color = colors.onSurfaceVariant)
                        if (large) {
                            Text(result.displayValue, style = TmtnType.display, color = colors.onSurface)
                            Text("cm", style = TmtnType.bodyLarge, color = colors.onSurfaceVariant)
                        } else Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(result.displayValue, style = TmtnType.display, color = colors.onSurface)
                            Text("cm", style = TmtnType.bodyLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    if (!large) TmtnMascot(R.drawable.beaver_standing, null, Modifier.size(88.dp), greet = false)
                }
                // Editorial ruler motif only: no invented clinical cutoffs, target, or percentile scale.
                Canvas(Modifier.fillMaxWidth().height(20.dp).clearAndSetSemantics {}) {
                    val ticks = 24
                    for (index in 0..ticks) {
                        val x = size.width * index / ticks
                        drawLine(if (index == 0 || index == ticks) colors.secondary else colors.outline,
                            Offset(x, 0f), Offset(x, if (index % 4 == 0) size.height else size.height * .45f), 2.dp.toPx())
                    }
                }
                Text(result.computedAt?.let {
                    DateTimeFormatter.ofPattern("yyyy.M.d 계산", Locale.KOREAN).withZone(ZoneId.systemDefault()).format(it)
                } ?: "계산일 정보 없음", style = TmtnType.caption, color = colors.onSurfaceVariant)
                Text("줄자로 잰 값과는 달라요", style = TmtnType.bodyLarge, color = colors.onSurface)
                Text("입력한 정보로 모델이 추정한 둘레예요. 실제 측정값과 차이가 있을 수 있어요.",
                    style = TmtnType.body, color = colors.onSurfaceVariant)
            }
            WaistEstimateUi.Loading -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.primary)
                    Text("추정값을 불러오고 있어요", style = TmtnType.body, color = colors.onSurfaceVariant)
                }
            }
            WaistEstimateUi.Unavailable -> {
                Text("아직 확인할 추정값이 없어요", style = TmtnType.bodyLarge, color = colors.onSurface)
                TmtnOutlinedButton("다시 확인", onRetry)
            }
            WaistEstimateUi.Failed -> {
                Text("추정값을 불러오지 못했어요", style = TmtnType.bodyLarge, color = colors.onSurface)
                Text("연결을 확인하고 다시 시도해 주세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                TmtnOutlinedButton("다시 시도", onRetry)
            }
        }
    }
}

@Composable
fun ReferenceWaistScreen(state: ReferenceState, result: WaistEstimateUi, onRetry: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar("허리둘레", onBack = { state.goBack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(20.dp)) {
                WaistEstimateArticle(result, onRetry)
            }
            ScoreActionRow("입력 정보 확인", "신체 정보 · 운동 정보") { state.openInputs() }
            Text("비진단용 참고 정보", style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
    }
}
