package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.CardRevealResponse
import com.tmtn.app.ui.common.tmtnMaterialDrawable
import com.tmtn.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 홈·공개·완료 다시보기가 함께 쓰는 카드. API 문구에 맞춰 높이가 늘어난다. */
@Composable
fun TmtnMissionCard(card: CardRevealResponse, date: LocalDate, compact: Boolean = false) {
    val forest = TmtnHomeColor.PaperInk
    val muted = TmtnHomeColor.PaperMuted
    val cream = TmtnHomeColor.Paper
    val rim = TmtnHomeColor.PaperRim
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().testTag(if (compact) "home-selected-card" else "mission-card")
        .then(if (compact) Modifier.heightIn(min = 418.dp) else Modifier).background(cream, shape)
        .border(1.dp, rim, shape)
        .padding(8.dp).border(1.dp, rim.copy(alpha = .4f), RoundedCornerShape(17.dp)).padding(if (compact) 16.dp else 21.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = if (compact) HomeCardArrangement else Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("오늘의 틈", style = TmtnType.label, color = forest, modifier = Modifier.weight(1f))
            Text(date.format(DateTimeFormatter.ofPattern("M. d. E", Locale.KOREAN)), style = TmtnType.caption, color = muted)
        }
        if (card.exec_type.startsWith("SENSOR_")) Text("움직임 감지 미션", style = TmtnType.label,
            color = ColorOnSurface, modifier = Modifier.background(ColorSecondaryContainer, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp))
        Box(Modifier.size(if (compact) 60.dp else 98.dp).border(1.dp, rim, CircleShape).padding(8.dp)
            .background(TmtnHomeColor.Emblem, CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(tmtnMaterialDrawable(card.five_element)), MATERIAL_NAMES[card.five_element]?.first,
                Modifier.size(if (compact) 42.dp else 72.dp))
        }
        Text(card.fortune_text?.takeIf { it.isNotBlank() } ?: card.title,
            style = TmtnType.cardMessage, color = forest,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = rim.copy(alpha = .45f))
            Text("TMTN", style = TmtnType.label, color = muted)
            HorizontalDivider(Modifier.weight(1f), color = rim.copy(alpha = .45f))
        }
        if (compact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("행운의 행동", style = TmtnType.caption, color = muted)
                Text(card.title, style = TmtnType.missionName, color = forest, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    card.lucky_location?.takeIf { it.isNotBlank() }?.let {
                        Text("행운의 장소", style = TmtnType.caption, color = muted)
                        Text(it, style = TmtnType.missionName, color = forest)
                    }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("오늘의 목표", style = TmtnType.caption, color = muted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(card.target_value.toString(), style = TmtnType.title, color = forest)
                        Text(card.unit, style = TmtnType.caption, color = muted)
                    }
                }
            }
        } else {
        MissionField("행운의 행동", card.title, forest, muted)
        card.lucky_location?.takeIf { it.isNotBlank() }?.let { MissionField("행운의 장소", it, forest, muted) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("오늘의 목표", style = TmtnType.caption, color = muted, modifier = Modifier.weight(1f))
            Text(card.target_value.toString(), style = TmtnType.headline, color = forest)
            Text(card.unit, style = TmtnType.label, color = muted, modifier = Modifier.padding(start = 5.dp))
        }
        }
        card.line_text?.takeIf { it.isNotBlank() }?.let {
            Column(Modifier.fillMaxWidth().background(TmtnHomeColor.PaperInset, RoundedCornerShape(12.dp)).padding(if (compact) 8.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 6.dp)) {
                Text("오늘의 한 줄", style = TmtnType.caption, color = muted)
                Text(it, style = if (compact) TmtnType.actionLabel else TmtnType.body, color = forest)
            }
        }
        if (card.state == "COMPLETED") {
            Text("실천 완료", style = TmtnType.label, color = forest,
                modifier = Modifier.testTag("mission-card-completed"))
        }
    }
}

/** 시안 C의 선택한 카드 전체. API/CSV 문구가 길거나 글자 크기가 크면 자연스럽게 늘어난다. */
@Composable
internal fun HomeMissionCard(card: CardRevealResponse, completed: Boolean = false, date: LocalDate = LocalDate.now(), action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TmtnMissionCard(card, date, compact = true)
        action?.invoke()
    }
}

/** 기본 높이에서는 여백을 균등 분배하고 긴 문구에서도 최소 8dp를 유지한다. */
private val HomeCardArrangement = object : Arrangement.Vertical {
    override val spacing = 8.dp
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        val gap = if (sizes.size > 1) ((totalSize - sizes.sum()) / (sizes.size - 1)).coerceAtLeast(spacing.roundToPx()) else 0
        var top = 0
        sizes.forEachIndexed { index, size ->
            outPositions[index] = top
            top += size + gap
        }
    }
}

@Composable
private fun MissionField(label: String, value: String, ink: Color, muted: Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = TmtnType.caption, color = muted)
        Text(value, style = TmtnType.bodyLarge, color = ink)
    }
}


