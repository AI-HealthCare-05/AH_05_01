package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.reference.ScorePercentilePresentation
import com.tmtn.app.ui.record.LegendDot
import com.tmtn.app.ui.record.dashedCircleBorder
import com.tmtn.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Home summaries use the existing state and callbacks. No requests or model conversion here. */
@Composable
internal fun TmtnIndexSummaryCard(state: CardHomeState, onOpenTuntunScore: () -> Unit = {}) {
    val colors = LocalTmtnColors.current
    val result = ScorePercentilePresentation.fromCurrent(
        state.tuntunIndexPresentationValue.value ?: state.tuntunIndexValue.value?.toDouble())
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface)
        .border(1.dp, colors.outlineVariant, shape)) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(ColorBrandForest))
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("틈튼지수", style = TmtnType.title, color = colors.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.clearAndSetSemantics {}) {
                    listOf(ColorBrandForest, colors.wood, colors.secondary).forEach {
                        Box(Modifier.size(5.dp).background(it, CircleShape))
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val largeText = LocalTmtnTextScale.current * LocalDensity.current.fontScale > 1.35f
                val showArtwork = !largeText && maxWidth >= 280.dp
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        when {
                            state.tuntunIndexLoadFailed.value -> Text("지수를 불러오지 못했어요. 연결을 확인해 주세요.",
                                style = TmtnType.body, color = colors.onSurfaceVariant)
                            result == null -> Text("신체 정보와 운동 정보를 입력하면 지수를 볼 수 있어요.",
                                style = TmtnType.body, color = colors.onSurfaceVariant)
                            else -> {
                                if (!result.isPreview) Text("100명 중", style = TmtnType.body, color = colors.onSurfaceVariant)
                                Text(buildAnnotatedString {
                                    withStyle(SpanStyle(color = colors.secondary, fontSize = TmtnType.display.fontSize * 1.3f,
                                        fontWeight = FontWeight.Bold)) {
                                        append(if (result.isPreview) result.scoreNumber else result.position.toString())
                                    }
                                    append(if (result.isPreview) " 점" else " 번째쯤")
                                }, style = TmtnType.bodyLarge.copy(lineHeight = TmtnType.display.lineHeight * 1.3f), color = colors.onSurface,
                                    modifier = Modifier.clearAndSetSemantics { contentDescription = result.reading })
                            }
                        }
                    }
                    // A small newspaper connects the home result to its editorial destination.
                    // Hide decorative art first when accessibility text needs the width.
                    if (showArtwork) Image(painterResource(R.drawable.score_news_bundle), null,
                        modifier = Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).tmtnClickable(onClick = onOpenTuntunScore)
                .padding(vertical = 12.dp), contentAlignment = Alignment.CenterEnd) {
                Text("지수 보기 ›", style = TmtnType.body, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun RecentSummaryListCard(state: CardHomeState) {
    val colors = LocalTmtnColors.current
    val today = state.displayDateLabel()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val records = state.recentWeek.value.associateBy { it.date }
    val failed = state.recentWeekLoadFailed.value
    val completedCount = days.count { records[it.toString()]?.status == "COMPLETED" }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("최근 7일", style = TmtnType.title, color = colors.onSurface, modifier = Modifier.semantics { heading() })
            when {
                failed -> Text("기록을 불러오지 못했어요", style = TmtnType.body, color = colors.onSurfaceVariant)
                records.isEmpty() -> Text("기록을 불러오는 중이에요", style = TmtnType.body, color = colors.onSurfaceVariant)
                else -> Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.secondary, fontSize = TmtnType.headline.fontSize, fontWeight = FontWeight.Bold)) {
                        append("${completedCount}일")
                    }
                    append(" 실천했어요")
                }, style = TmtnType.bodyLarge, color = colors.onSurface)
            }
            Text("${days.first().monthValue}월 ${days.first().dayOfMonth}일 – ${today.monthValue}월 ${today.dayOfMonth}일",
                style = TmtnType.caption, color = colors.onSurfaceVariant)
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
              BoxWithConstraints(Modifier.fillMaxWidth()) {
                val scale = LocalTmtnTextScale.current * LocalDensity.current.fontScale
                val cellWidth = maxOf((maxWidth - 4.dp * 6) / 7, 36.dp * scale)
                val scroll = rememberScrollState()
                LaunchedEffect(today, scale, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
                Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    days.forEach { date ->
                        HomeRecordDate(date, if (failed) null else records[date.toString()]?.status,
                            date == today, Modifier.width(cellWidth))
                    }
                }
              }
              // Reuse the record calendar's legend and status styling.
              FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  LegendDot(colors.onSurface, "실천")
                  LegendDot(colors.disabledContainer, "쉼", outlineColor = colors.onSurface)
                  LegendDot(Color.Transparent, "미완료", dashed = true, outlineColor = colors.outline)
                  LegendDot(Color.Transparent, "오늘", outlineColor = colors.secondary)
              }
            }
        }
        HomeDamSummary(state.companionStage.value)
    }
}

@Composable
private fun HomeRecordDate(date: LocalDate, status: String?, isToday: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalTmtnColors.current
    val scale = LocalTmtnTextScale.current * LocalDensity.current.fontScale
    Column(modifier.clearAndSetSemantics {
        contentDescription = "${date.monthValue}월 ${date.dayOfMonth}일" + (if (isToday) " 오늘, " else ", ") + when (status) {
            "COMPLETED" -> "실천"; "REST" -> "쉼"; "INCOMPLETE" -> "미완료"
            null -> "기록 확인 중"; else -> "기록 없음"
        }
    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(date.format(DateTimeFormatter.ofPattern("E", Locale.KOREAN)), style = TmtnType.label,
            color = colors.onSurfaceVariant)
        Box(Modifier.size(36.dp * scale)
            .then(if (isToday) Modifier.border(2.dp, colors.secondary, CircleShape).padding(2.dp) else Modifier)
            .then(when (status) {
                "COMPLETED" -> Modifier.background(colors.onSurface, CircleShape)
                "REST" -> Modifier.background(colors.disabledContainer, CircleShape).border(1.5.dp, colors.onSurface, CircleShape)
                "INCOMPLETE" -> Modifier.dashedCircleBorder(1.5.dp, colors.outline)
                else -> Modifier
            }), contentAlignment = Alignment.Center) {
            Text(date.dayOfMonth.toString(), style = TmtnType.body, fontWeight = FontWeight.SemiBold,
                color = if (status == "COMPLETED") colors.background else colors.onSurface)
        }
    }
}

@Composable
private fun HomeDamSummary(stage: Int) {
    val colors = LocalTmtnColors.current
    val shape = RoundedCornerShape(18.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().clip(shape).background(colors.woodContainer)
        .border(1.dp, colors.outlineVariant, shape).padding(20.dp)) {
        val largeText = LocalTmtnTextScale.current * LocalDensity.current.fontScale > 1.35f
        val inlineArtwork = !largeText && maxWidth >= 280.dp
        // The dam tab also uses its first scene at stage zero; the text keeps the actual stage.
        val artwork = when (stage.coerceIn(1, 5)) {
            1 -> R.drawable.dam_stage_1
            2 -> R.drawable.dam_stage_2
            3 -> R.drawable.dam_stage_3
            4 -> R.drawable.dam_stage_4
            else -> R.drawable.dam_stage_5
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(buildAnnotatedString {
                        append("댐 ")
                        withStyle(SpanStyle(color = colors.secondary, fontSize = TmtnType.display.fontSize, fontWeight = FontWeight.Bold)) {
                            append("${stage}단계")
                        }
                    }, style = TmtnType.title.copy(lineHeight = TmtnType.display.lineHeight), color = colors.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.clearAndSetSemantics {}) {
                        repeat(5) { index ->
                            Box(Modifier.width(14.dp).height(4.dp).background(
                                if (index < stage) colors.wood else colors.outlineVariant, RoundedCornerShape(2.dp)))
                        }
                    }
                }
                if (inlineArtwork) Image(painterResource(artwork), null,
                    contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter,
                    modifier = Modifier.width(136.dp).height(96.dp))
            }
            if (!inlineArtwork) Image(painterResource(artwork), null,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(104.dp))
        }
    }
}
