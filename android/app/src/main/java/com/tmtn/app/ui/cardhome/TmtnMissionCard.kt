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

/** Live mission text sits on native, expandable paper. Artwork never contains mission copy. */
@Composable
fun TmtnMissionCard(card: CardRevealResponse, date: LocalDate, compact: Boolean = false) {
    if (compact) {
        HomeMissionCard(card)
        return
    }
    val forest = Color(0xFF315342)
    val muted = Color(0xFF696456)
    val cream = Color(0xFFFFF8E9)
    val rim = Color(0xFFAC8E61)
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().background(cream, shape)
        .border(1.dp, rim, shape)
        .padding(9.dp).border(1.dp, rim.copy(alpha = .4f), RoundedCornerShape(17.dp)).padding(if (compact) 15.dp else 21.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("오늘의 틈", style = TmtnType.label, color = forest, modifier = Modifier.weight(1f))
            Text(date.format(DateTimeFormatter.ofPattern("M. d. E", Locale.KOREAN)), style = TmtnType.caption, color = muted)
        }
        if (card.exec_type.startsWith("SENSOR_")) Text("움직임 감지 미션", style = TmtnType.label,
            color = ColorOnSurface, modifier = Modifier.background(ColorSecondaryContainer, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp))
        Box(Modifier.size(if (compact) 70.dp else 98.dp).border(1.dp, rim, CircleShape).padding(8.dp)
            .background(Color(0xFFF2E5C9), CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(tmtnMaterialDrawable(card.five_element)), MATERIAL_NAMES[card.five_element]?.first,
                Modifier.size(if (compact) 52.dp else 72.dp))
        }
        Text(if (!compact && !card.fortune_text.isNullOrBlank()) card.fortune_text else card.title,
            style = if (compact) TmtnType.title else TmtnType.cardMessage, color = forest,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = rim.copy(alpha = .45f))
            Text("TMTN", style = TmtnType.label, color = muted)
            HorizontalDivider(Modifier.weight(1f), color = rim.copy(alpha = .45f))
        }
        if (!compact) MissionField("행운의 행동", card.title, forest, muted)
        card.lucky_location?.takeIf { it.isNotBlank() }?.let { MissionField("행운의 장소", it, forest, muted) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("오늘의 목표", style = TmtnType.caption, color = muted, modifier = Modifier.weight(1f))
            Text(card.target_value.toString(), style = TmtnType.headline, color = forest)
            Text(card.unit, style = TmtnType.label, color = muted, modifier = Modifier.padding(start = 5.dp))
        }
        if (!compact) card.line_text?.takeIf { it.isNotBlank() }?.let {
            Column(Modifier.fillMaxWidth().background(Color(0xFFEEEAD7), RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("오늘의 한 줄", style = TmtnType.caption, color = muted)
                Text(it, style = TmtnType.body, color = forest)
            }
        }
    }
}

/** Figma B23: compact mission summary, not the tall collectible card face. */
@Composable
internal fun HomeMissionCard(card: CardRevealResponse, completed: Boolean = false, action: (@Composable () -> Unit)? = null) {
    val colors = LocalTmtnColors.current
    val measured = card.exec_type.startsWith("SENSOR_")
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.fillMaxWidth().background(colors.surface, shape)
        .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (measured) Text("틈튼 움직임 인식", style = TmtnType.label, color = colors.onSurface,
            modifier = Modifier.background(colors.secondaryContainer, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
        Text(if (completed) "오늘의 카드 · 실천 완료" else "오늘의 틈 · ${card.domain ?: "작은 실천"}", style = TmtnType.label, color = colors.onSurface)
        Text(card.title, style = TmtnType.title, color = colors.onSurface, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(tmtnMaterialDrawable(card.five_element)), null, Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (!completed) Text(if (measured) "움직인 만큼 기록해요." else "${card.target_value}${card.unit} 실천해요.",
                    style = TmtnType.caption, color = colors.onSurface)
                Text(if (completed) "${MATERIAL_NAMES[card.five_element]?.first ?: "재료"}를 받았어요."
                    else "완료하면 ${MATERIAL_NAMES[card.five_element]?.first ?: "재료"}를 받아요.",
                    style = TmtnType.caption, color = colors.onSurface)
            }
        }
        action?.invoke()
        if (!completed) Text("고른 카드는 오늘 여기서 계속 볼 수 있어요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun MissionField(label: String, value: String, ink: Color, muted: Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = TmtnType.caption, color = muted)
        Text(value, style = TmtnType.bodyLarge, color = ink)
    }
}
