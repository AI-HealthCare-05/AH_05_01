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
import com.tmtn.app.ui.common.TmtnSurfaceRole
import com.tmtn.app.ui.common.tmtnSurface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer

/** The newspaper entry borrows its own masthead rules and paper tone so it reads as print, not as another panel. */
@Composable
internal fun TmtnIndexSummaryCard(state: CardHomeState, onOpenTuntunScore: () -> Unit = {}) {
    val colors = LocalTmtnColors.current
    val paper = com.tmtn.app.ui.theme.ColorArtworkPaper
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(paper)
        .border(TmtnLayout.Hairline, colors.outlineVariant, shape)
        .tmtnClickable(onClick = onOpenTuntunScore)
        .padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("TMTN DAILY", style = TmtnType.label, color = ColorBrandForest)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(colors.onSurface))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.onSurface))
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("틈튼일보", style = TmtnType.editorialHeadline, color = colors.onSurface)
                Text("나의 일주일이 한 장의 소식으로", style = TmtnType.caption, color = colors.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Text("이번 호 펼치기", style = TmtnType.label, color = ColorBrandForest)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = ColorBrandForest)
                }
            }
            Image(painterResource(R.drawable.beaver_newspaper), null, Modifier.size(100.dp), contentScale = ContentScale.Fit)
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
            Text("${days.first().monthValue}월 ${days.first().dayOfMonth}일 ~ ${today.monthValue}월 ${today.dayOfMonth}일",
                style = TmtnType.caption, color = colors.onSurfaceVariant)
            Column(Modifier.fillMaxWidth().tmtnSurface(TmtnSurfaceRole.Panel, outlined = false)
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
