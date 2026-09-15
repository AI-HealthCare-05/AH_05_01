package com.tmtn.app.ui.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmtn.app.R
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.common.TmtnActionButton
import com.tmtn.app.ui.common.TmtnActionStyle
import com.tmtn.app.ui.reference.ScorePeerPositionUi
import com.tmtn.app.ui.reference.WaistEstimateUi
import com.tmtn.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Paper = Color(0xFFF7F4EE)
private val Ink = Color(0xFF2C2C2C)
private val Muted: Color @Composable get() = LocalTmtnColors.current.onSurfaceVariant
private val Forest = Color(0xFF3F5D4B)
private val Orange = Color(0xFFFF7A1A)
private val Hairline = Color(0xFFDEDAD1)

/** Native, read-only edition. No HTML bridge, model call, invented rank or new persistence. */
@Composable
fun JournalScreen(
    weekly: JournalLoad<WeeklyReportResponse>,
    collection: JournalLoad<List<CardHistoryItem>>,
    today: JournalLoad<JournalToday>,
    score: TuntunScorePeerV2Response?,
    waist: WaistEstimateUi,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onGoPickCard: () -> Unit,
    onEditInformation: () -> Unit,
    onRetryWaist: () -> Unit,
    peerPositions: List<ScorePeerPositionUi> = emptyList(),
    requestedEdition: Int? = null,
    onEditionOpened: () -> Unit = {},
    exercises: JournalLoad<List<ExerciseMissionRecordItem>>? = null,
) {
    var edition by rememberSaveable { mutableIntStateOf(0) }
    val editionStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val weeklyScroll = rememberScrollState()
    val dailyScroll = rememberScrollState()
    LaunchedEffect(requestedEdition) {
        if (requestedEdition != null) {
            edition = requestedEdition.coerceIn(0, 1)
            (if (edition == 0) weeklyScroll else dailyScroll).scrollTo(0)
            onEditionOpened()
        }
    }
    val report = (weekly as? JournalLoad.Ready)?.value
    val allCards = (collection as? JournalLoad.Ready)?.value.orEmpty()
    val cards = remember(allCards, report) { issueCards(allCards, report) }
    Column(Modifier.fillMaxSize().background(Paper).testTag("journal-screen")) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("틈튼일보", style = TmtnType.title, color = Ink, modifier = Modifier.weight(1f))
            TextButton(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) "불러오는 중" else "새로고침", style = TmtnType.label, color = Muted)
            }
        }
        JournalTabs(listOf("주간면", "일간면"), edition, { edition = it }, Modifier.background(Color.White))
        editionStates.SaveableStateProvider(edition) {
        Column(Modifier.weight(1f).verticalScroll(if (edition == 0) weeklyScroll else dailyScroll)
            .padding(horizontal = 22.dp).padding(top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Masthead(edition, report, (today as? JournalLoad.Ready)?.value?.window?.service_date)
            if (edition == 0) {
                WeeklyCover(report, cards)
                SectionTitle("01", "실천한 발자국")
                WeeklyFootprints(weekly, collection, cards, onRefresh, exercises)
                SectionTitle("02", "생활 읽을거리")
                LivingArticles()
                SectionTitle("03", "틈튼이의 작은 수리일지")
                RepairDiary()
            } else {
                DailyCover(today, onGoPickCard, onRefresh)
                (today as? JournalLoad.Ready)?.value?.window?.service_date?.let { date ->
                    ExtraExerciseClippings(exercises, date, onRefresh)
                }
                QuoteBlock("오늘의 한 문장", dailyEditorial(today).sentence)
                SectionTitle("01", "오늘의 카드 옆에")
                DailyArticle(today)
                SectionTitle("02", "잠깐 읽고, 가볍게 실천")
                TableArticle()
            }
            SectionTitle(if (edition == 0) "04" else "03", "이번 호에 끼워둔 내 기록")
            PersonalRecord(score, waist, cards, peerPositions, onEditInformation, onRetryWaist)
            NextCard(today, onGoPickCard)
            Text("작은 실천을 모아, 매일 한 장.\n틈튼이가 전하는 생활 소식", style = TmtnType.caption,
                color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        }
    }
}

@Composable
private fun Masthead(edition: Int, report: WeeklyReportResponse?, serviceDate: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("작은 실천을 모아, 매일 한 장.", style = TmtnType.label, color = Ink)
        Text("틈튼일보", style = TmtnType.display.copy(fontSize = 40.sp * LocalTmtnTextScale.current,
            lineHeight = 52.sp * LocalTmtnTextScale.current, letterSpacing = 2.sp), color = Ink,
            modifier = Modifier.semantics { heading() })
        HorizontalDivider(thickness = 2.dp, color = Ink)
        Spacer(Modifier.height(3.dp))
        HorizontalDivider(thickness = .5.dp, color = Ink)
        Spacer(Modifier.height(10.dp))
        Text(if (edition == 0) "주간면  ·  ${report?.let { "${shortDate(it.start_date)} — ${shortDate(it.end_date)}" } ?: "나의 일곱 날"}"
            else "일간면  ·  ${shortDate(serviceDate ?: LocalDate.now(JournalZone).toString())}", style = TmtnType.caption, color = Muted)
    }
}

@Composable
private fun WeeklyCover(report: WeeklyReportResponse?, cards: List<CardHistoryItem>) {
    val lead = weeklyLead(report, cards)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Kicker("이번 주의 1면")
        Text(lead.title, style = TmtnType.headline.copy(fontWeight = FontWeight.Bold), color = Ink,
            modifier = Modifier.semantics { heading() })
        Box(Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(20.dp)).background(Color.White)) {
            com.tmtn.app.ui.common.DamArtwork(0, Modifier.align(Alignment.BottomStart).fillMaxWidth(.77f),
                description = "작은 빈틈부터 고쳐 가는 댐")
            Image(painterResource(R.drawable.beaver_fixing), "댐의 작은 틈을 고치는 틈튼이", Modifier.align(Alignment.BottomEnd).size(148.dp), contentScale = ContentScale.Fit)
        }
        Text(lead.body, style = TmtnType.body, color = Ink)
        Text("나의 실천 기록으로 엮은 이야기", style = TmtnType.caption, color = Muted)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun WeeklyFootprints(weekly: JournalLoad<WeeklyReportResponse>, collection: JournalLoad<List<CardHistoryItem>>,
    cards: List<CardHistoryItem>, onRetry: () -> Unit, exercises: JournalLoad<List<ExerciseMissionRecordItem>>?) {
    when (weekly) {
        JournalLoad.Loading -> JournalNotice("이번 주 기록을 펼치고 있어요.")
        JournalLoad.Failed -> JournalNotice("기록을 불러오지 못했어요. 아래 기사는 계속 읽을 수 있어요.", "다시 불러오기", onRetry)
        is JournalLoad.Ready -> {
            val report = weekly.value
            var selected by rememberSaveable(report.start_date, report.end_date) { mutableStateOf<String?>(null) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${report.completed_count.coerceIn(0, report.total_days.coerceAtLeast(0))}", style = TmtnType.display, color = Ink)
                    Text("일 실천", style = TmtnType.label, color = Ink, modifier = Modifier.padding(start = 6.dp, bottom = 5.dp))
                    Spacer(Modifier.weight(1f))
                    Text("쉼 ${report.days.count { it.status == "REST" }}일", style = TmtnType.caption, color = Muted)
                }
                val dates = remember(report.start_date, report.end_date) {
                    val start = runCatching { LocalDate.parse(report.start_date) }.getOrNull()
                    val end = runCatching { LocalDate.parse(report.end_date) }.getOrNull()
                    if (start == null || end == null) emptyList() else (0L..6L).map { start.plusDays(it) }.filter { it <= end }
                }
                val dateScale = androidx.compose.ui.platform.LocalDensity.current.fontScale * com.tmtn.app.ui.theme.LocalTmtnTextScale.current
                val dateSize = (34 * dateScale.coerceAtLeast(1f)).dp
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Seven dates fit at normal type size; enlarged type can scroll without squeezing numerals.
                val cellWidth = maxOf(maxWidth / 7, dateSize)
                Row(Modifier.fillMaxWidth().testTag("journal-week-dates").horizontalScroll(rememberScrollState()).selectableGroup()) {
                    dates.forEach { date ->
                        val status = report.days.firstOrNull { it.date == date.toString() }?.status
                        val isSelected = selected == date.toString()
                        Column(Modifier.width(cellWidth).clip(RoundedCornerShape(12.dp))
                            .selectable(isSelected, role = Role.Tab) { selected = if (isSelected) null else date.toString() }
                            .semantics { contentDescription = "${date.monthValue}월 ${date.dayOfMonth}일, ${statusLabel(status)}" }
                            .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(date.format(DateTimeFormatter.ofPattern("E", Locale.KOREAN)), style = TmtnType.caption, color = Muted)
                            Box(Modifier.size(dateSize).border(if (isSelected) 2.dp else 1.dp,
                                if (isSelected) Orange else if (status == "REST") Ink else Color.Transparent, CircleShape)
                                .padding(3.dp).background(if (status == "COMPLETED") Ink else Paper, CircleShape), contentAlignment = Alignment.Center) {
                                Text(date.dayOfMonth.toString(), style = TmtnType.label, color = if (status == "COMPLETED") Color.White else Ink)
                            }
                        }
                    }
                }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Legend("실천", true); Legend("쉼", false)
                    Text("날짜를 눌러 읽기", style = TmtnType.caption, color = Muted)
                }
                HorizontalDivider(color = Hairline)
                val visible = cards.filter { selected == null || completionDate(it.completed_at)?.toString() == selected }
                Kicker(selected?.let { "${shortDate(it)}에 남긴 카드" } ?: "이번 주에 해낸 카드")
                when {
                    collection is JournalLoad.Failed -> JournalNotice("카드 목록을 불러오지 못했어요.", "다시 불러오기", onRetry)
                    collection is JournalLoad.Loading -> Text("카드를 모으고 있어요.", style = TmtnType.body, color = Muted)
                    visible.isEmpty() -> Text(if (selected != null) "이날 완료한 카드가 없어요." else "완료한 카드가 여기에 차곡차곡 남아요.", style = TmtnType.body, color = Muted)
                    else -> {
                        var expanded by rememberSaveable(selected) { mutableStateOf(false) }
                        visible.take(if (expanded) visible.size else 3).forEach { card -> MissionClipping(card) }
                        if (visible.size > 3) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (expanded) "접기" else "${visible.size}장 모두 보기", style = TmtnType.label, color = Ink)
                        }
                    }
                }
                ExtraExerciseClippings(exercises, selected, onRetry)
            }
            val materials = report.materials_this_week.filter { it.count > 0 }
            if (materials.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("그 실천으로 모은 재료", style = TmtnType.label, color = Ink)
                materials.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { material ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(painterResource(materialArt(material.element)), null, Modifier.size(44.dp))
                                Text("${material.material_name} ${material.count}개", style = TmtnType.caption, color = Ink, textAlign = TextAlign.Center)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraExerciseClippings(records: JournalLoad<List<ExerciseMissionRecordItem>>?, date: String?, onRetry: () -> Unit) {
    when (records) {
        JournalLoad.Failed -> JournalNotice("틈새 운동 기록을 불러오지 못했어요.", "다시 불러오기", onRetry)
        is JournalLoad.Ready -> {
            val visible = records.value.filter { date == null || it.service_date == date }
            if (visible.isNotEmpty()) {
                var expanded by rememberSaveable(date) { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().testTag("journal-extra-exercises"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HorizontalDivider(color = Hairline)
                    Kicker("틈새 운동 · ${visible.size}회")
                    Text("카드를 마친 뒤,\n조금 더 움직였어요.", style = TmtnType.title, color = Ink)
                    visible.take(if (expanded) visible.size else 3).forEach { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(painterResource(materialArt(item.five_element)), null, Modifier.size(40.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.title, style = TmtnType.label, color = Ink)
                                Text("${shortDate(item.service_date)} · ${item.material_name}", style = TmtnType.caption, color = Muted)
                            }
                        }
                    }
                    if (visible.size > 3) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (expanded) "틈새 운동 접기" else "틈새 운동 ${visible.size}회 모두 보기", style = TmtnType.label, color = Ink)
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun Legend(text: String, filled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(11.dp).background(if (filled) Ink else Color.Transparent, CircleShape).border(1.5.dp, Ink, CircleShape))
        Text(text, style = TmtnType.caption, color = Muted)
    }
}

@Composable
private fun LivingArticles() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Kicker("움직임에 관하여")
        Text("운동할 시간,\n일상 뒤에 붙여볼까요?", style = TmtnType.title, color = Ink)
        Row(Modifier.fillMaxWidth().background(Color(0xFFE7ECDF), RoundedCornerShape(16.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("식사 뒤에", style = TmtnType.label, color = Forest)
                Spacer(Modifier.height(8.dp))
                Text("익숙한 길\n한 바퀴", style = TmtnType.title, color = Ink)
            }
            Image(painterResource(R.drawable.beaver_walking), "산책하는 틈튼이", Modifier.size(110.dp))
        }
        Text("시간을 따로 내기 어려운 날에는 늘 하던 일 뒤에 짧은 움직임을 붙여보세요. ‘점심을 먹고 나서’처럼 시작할 때를 정해두는 거예요.", style = TmtnType.body, color = Ink)
        HorizontalDivider(color = Hairline)
        TableArticle()
        HorizontalDivider(color = Hairline)
        Kicker("꾸준함에 관하여")
        Text("쉬어간 다음 날엔,\n한 장만 꺼내도 좋아요.", style = TmtnType.title, color = Ink)
        Image(painterResource(R.drawable.beaver_rest), "편안하게 쉬는 틈튼이", Modifier.fillMaxWidth().height(146.dp))
        Text("비어 있는 하루를 밀린 숙제처럼 채우지 않아도 돼요. 지난번에 해낸 카드와 모아둔 재료는 남아 있으니까요.", style = TmtnType.body, color = Ink)
        QuoteBlock(null, "어제 못 한 만큼 말고,\n오늘 할 수 있는 만큼만.")
    }
}

@Composable
private fun TableArticle() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Kicker("식탁에서 · 혈압을 돌보는 습관")
        Text("소금통보다,\n첫 한입을 먼저.", style = TmtnType.title, color = Ink)
        Row(Modifier.fillMaxWidth().background(Color(0xFFF4E3D0), RoundedCornerShape(12.dp)).padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Text("맛보고", style = TmtnType.title, color = Ink)
            Text("→", style = TmtnType.title, color = Ink)
            Text("정하기", style = TmtnType.title, color = Ink)
        }
        Text("소금이나 소스를 더하기 전에 한입 맛보세요. 이미 간이 맞는다면 그대로 먹어보는 거예요. 평소 무심코 더하던 한 번을 줄여볼 수 있어요.", style = TmtnType.body, color = Ink)
        Text("오늘의 작은 실천 · 더하기 전에 한입 맛보기", style = TmtnType.caption, color = Muted)
    }
}

@Composable
private fun RepairDiary() {
    val panels = listOf(Triple(R.drawable.beaver_card, "오늘은 이 한 장부터!", "내 하루에 들어갈 행동을 고르고"),
        Triple(R.drawable.beaver_fixing, "이 틈에 딱 맞겠는걸?", "실천으로 얻은 재료로 한 곳을 메우고"),
        Triple(R.drawable.beaver_rest, "내일 또 이어가자.", "오늘의 작은 수리를 마칩니다"))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        panels.forEachIndexed { index, panel ->
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(panel.first), null, Modifier.size(80.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("0${index + 1}  ${panel.second}", style = TmtnType.label, color = Ink)
                    Text(panel.third, style = TmtnType.caption, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun DailyCover(today: JournalLoad<JournalToday>, onGo: () -> Unit, onRetry: () -> Unit) {
    val value = (today as? JournalLoad.Ready)?.value
    val rest = value?.window?.is_rest_day == true
    val done = value?.challengeState == "COMPLETED"
    val editorial = dailyEditorial(today)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Kicker("오늘의 1면")
        Text(editorial.title, style = TmtnType.headline, color = Ink)
        Image(painterResource(if (rest) R.drawable.beaver_rest else if (done) R.drawable.beaver_cheer else R.drawable.beaver_card),
            null, Modifier.fillMaxWidth().height(164.dp))
        when (today) {
            JournalLoad.Loading -> JournalNotice("오늘의 카드를 불러오고 있어요.")
            JournalLoad.Failed -> JournalNotice("오늘의 카드를 확인하지 못했어요.", "다시 불러오기", onRetry)
            is JournalLoad.Ready -> if (today.value.card != null) {
                val card = today.value.card
                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp))
                    .then(if (card.exec_type.startsWith("SENSOR_")) Modifier.border(1.dp, Orange, RoundedCornerShape(16.dp)) else Modifier)
                    .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Kicker(if (done) "오늘 해낸 카드" else "오늘 고른 카드", Modifier.weight(1f))
                        Image(painterResource(materialArt(card.five_element)), null, Modifier.size(44.dp))
                    }
                    Text(card.title, style = TmtnType.title, color = Ink)
                    if (card.exec_type.startsWith("SENSOR_")) Text("움직임 감지 · 움직인 만큼 기록해요", style = TmtnType.caption, color = Ink)
                    card.line_text?.takeIf { it.isNotBlank() }?.let { Text(it, style = TmtnType.body, color = Muted) }
                }
            } else if (today.value.window.challenge_id != null) {
                JournalNotice(if (done) "해낸 카드의 내용을 불러오지 못했어요." else "고른 카드의 내용을 불러오지 못했어요.",
                    "다시 불러오기", onRetry)
            } else Text(when {
                rest -> "오늘은 재료를 그대로 두고 쉬어가요. 내일 다시 한 장을 만나면 돼요."
                today.value.window.is_given_up -> "오늘은 여기까지. 다음 카드를 만날 때 다시 시작해요."
                else -> "오늘 할 수 있는 작은 행동을 카드에서 만나보세요."
            }, style = TmtnType.body, color = Ink)
        }
        TmtnActionButton(editorial.action, onGo, TmtnActionStyle.Primary)
    }
}

@Composable
private fun DailyArticle(today: JournalLoad<JournalToday>) {
    val editorial = dailyEditorial(today)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(editorial.articleTitle, style = TmtnType.title, color = Ink)
        Text(editorial.articleBody, style = TmtnType.body, color = Ink)
    }
}

@Composable
private fun PersonalRecord(score: TuntunScorePeerV2Response?, waist: WaistEstimateUi, cards: List<CardHistoryItem>,
    peers: List<ScorePeerPositionUi>, onEdit: () -> Unit, onRetryWaist: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var domain by rememberSaveable { mutableIntStateOf(3) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        JournalTabs(listOf("네 가지 이야기", "허리둘레"), tab, { tab = it })
        if (tab == 1) WaistArticle(waist, onEdit, onRetryWaist) else {
            val available = journalCompositeValue(score)
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Kicker("이번 호의")
                    Text("틈튼지수", style = TmtnType.title, color = Ink)
                }
                Text(available?.let { String.format(Locale.KOREAN, "%.1f", it) } ?: "—", style = TmtnType.display, color = Ink)
                if (available != null) Text("점", style = TmtnType.label, color = Muted, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
            }
            if (available == null) Text("지수가 준비되면 여기에 함께 담아둘게요.", style = TmtnType.caption, color = Muted)
            val names = listOf("신체", "당뇨", "고혈압", "생활습관")
            val keys = listOf("physical", "diabetes", "hypertension", "lifestyle")
            JournalTabs(names, domain, { domain = it })
            val peerText = journalRankText(score, keys[domain])
            if (peerText != null) {
                Text(peerText, style = TmtnType.title, color = Ink)
            } else {
                Text("아직 비교 결과가 없어요.", style = TmtnType.caption, color = Muted)
            }
            // Domain titles are editorial navigation, never fabricated personalized XAI claims.
            val title = listOf("오늘의 나를 알고,\n편한 속도를 찾아요.", "일상에 움직임을\n남겨두는 방법.", "익숙한 작은 습관,\n한 번 더 돌아봐요.", if (cards.isNotEmpty()) "해낸 카드마다,\n내 이야기가 있어요." else "나에게 맞는 실천을\n한 장씩 찾아봐요.")[domain]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = TmtnType.title, color = Ink, modifier = Modifier.weight(1f))
                Image(painterResource(if (domain == 0) R.drawable.beaver_wave else R.drawable.beaver_newspaper), null, Modifier.size(92.dp).clip(androidx.compose.foundation.shape.CircleShape))
            }
            Text(when (domain) {
                0 -> "몸의 정보는 오늘 나에게 편한 실천을 고를 때 함께 살펴봐요. 최근 키나 몸무게가 달라졌다면 새로 알려주세요."
                1 -> "길게 시간을 내기 어려운 날에는 익숙한 일상 뒤에 움직임을 붙여보세요. 이번 주에 편하게 해낸 카드가 있다면 다시 떠올려봐도 좋아요."
                2 -> "식탁에서는 소스를 더하기 전에 한입 맛보기. 움직일 때는 오늘 내 몸에 편한 속도 찾기. 반복하기 편한 것부터 골라봐요."
                else -> if (cards.isNotEmpty()) "이번 주에 해낸 카드들을 여기에도 끼워뒀어요. 쉽게 시작했던 카드, 다시 하고 싶은 카드를 찾아보세요." else "좋은 습관을 한꺼번에 채울 필요는 없어요. 오늘 카드 한 장으로 시작해보세요."
            }, style = TmtnType.body, color = Ink)
            if (cards.isNotEmpty()) {
                Kicker("이번 주에 해낸 실천")
                cards.take(2).forEach { MissionClipping(it) }
            }
            TextButton(onClick = onEdit) { Text("내 몸 · 운동 정보 살펴보기  →", style = TmtnType.label, color = Forest) }
            if (peerText != null) Text("등수는 진단이나 질병이 생길 확률이 아니에요.", style = TmtnType.caption, color = Muted)
        }
    }
}

@Composable
private fun WaistArticle(waist: WaistEstimateUi, onEdit: () -> Unit, onRetry: () -> Unit) {
    Kicker("작은 별지 · 몸의 기록")
    Text("숫자는 짧게 살피고,\n실천은 내 속도로.", style = TmtnType.title, color = Ink)
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("허리둘레 추정", style = TmtnType.label, color = Muted)
            if (waist is WaistEstimateUi.Available) {
                Text(waist.displayValue, style = TmtnType.display, color = Ink)
                Text("cm · 추정값", style = TmtnType.label, color = Muted)
            } else Text(if (waist is WaistEstimateUi.Loading) "불러오는 중" else "아직 결과가 없어요", style = TmtnType.bodyLarge, color = Ink)
        }
        Image(painterResource(R.drawable.journal_beaver_waist), null, Modifier.size(100.dp))
    }
    if (waist is WaistEstimateUi.Available) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val step = size.width / 32
            repeat(33) { i -> drawLine(if (i == 16) Orange else Hairline, Offset(i * step, 0f), Offset(i * step, if (i % 4 == 0) size.height else size.height * .45f), 2.dp.toPx()) }
        }
        Text("직접 잰 허리둘레와는 달라요. 신체지수와 별도로 살펴보는 기록이에요.", style = TmtnType.caption, color = Muted)
        Text("오늘 내 몸에 편했던 움직임도 함께 기억해봐요. 숫자를 빨리 바꾸려 하기보다, 내일도 할 수 있는 실천을 골라보세요.", style = TmtnType.body, color = Ink)
    } else if (waist is WaistEstimateUi.Failed) JournalNotice("허리둘레를 불러오지 못했어요.", "다시 불러오기", onRetry)
    else if (waist is WaistEstimateUi.Unavailable) Text("허리둘레 추정이 준비되지 않았어요. 입력한 몸의 정보를 확인할 수 있어요.", style = TmtnType.body, color = Ink)
    TextButton(onClick = onEdit) { Text("몸의 정보 확인하기  →", style = TmtnType.label, color = Forest) }
    QuoteBlock(null, "오늘은 네 속도로 해보자.\n나는 옆에서 같이 갈게.")
}

@Composable
private fun MissionClipping(card: CardHistoryItem) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(materialArt(card.five_element)), null, Modifier.size(36.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(card.title, style = TmtnType.label, color = Ink)
            Text("${shortDate(card.completed_at)} · ${card.material_name}", style = TmtnType.caption, color = Muted)
        }
    }
}

internal fun materialArt(element: String): Int = com.tmtn.app.ui.common.tmtnMaterialDrawable(element)

@Composable
private fun NextCard(today: JournalLoad<JournalToday>, onGo: () -> Unit) {
    val editorial = dailyEditorial(today)
    val returnHome = editorial.action != "오늘의 카드로 가기"
    Column(Modifier.fillMaxWidth().background(Color(0xFFE7ECDF), RoundedCornerShape(18.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Kicker(if (returnHome) "신문을 덮으며" else "신문을 덮고, 오늘 한 장")
        Text(if (returnHome) "내 댐 곁으로\n돌아갈까요?" else "내 하루에 들어갈\n작은 행동을 만나봐요.", style = TmtnType.title, color = Ink)
        Text(if (returnHome) "오늘의 카드와 모아둔 재료가 기다려요." else "오늘 가능한 만큼, 카드에서 이어가요.", style = TmtnType.body, color = Ink)
        TmtnActionButton(editorial.action, onGo, TmtnActionStyle.Primary)
    }
}

@Composable
private fun JournalTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().selectableGroup()) {
        labels.forEachIndexed { index, label ->
            Column(Modifier.weight(1f).selectable(selected == index, role = Role.Tab, onClick = {
                if (selected != index) com.tmtn.app.audio.TmtnAudio.play(com.tmtn.app.audio.TmtnSound.Paper)
                onSelect(index)
            })
                .heightIn(min = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = TmtnType.label, color = if (selected == index) Ink else Muted,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 3.dp, vertical = 12.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected == index) Orange else Hairline))
            }
        }
    }
}

@Composable
private fun SectionTitle(number: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(thickness = 1.dp, color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Text(number, style = TmtnType.label, color = Forest, modifier = Modifier.background(Color(0xFFFFE5D1), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp))
            Text(text, style = TmtnType.title, color = Ink, modifier = Modifier.weight(1f).semantics { heading() })
        }
    }
}

@Composable
private fun QuoteBlock(label: String?, quote: String) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        label?.let { Kicker(it) }
        Box(Modifier.width(28.dp).height(3.dp).background(Orange))
        Text("“$quote”", style = TmtnType.bodyLarge, color = Forest)
        Text("틈튼이의 한마디", style = TmtnType.caption, color = Muted)
    }
}

@Composable
private fun Kicker(text: String, modifier: Modifier = Modifier) = Text(text, style = TmtnType.label, color = Forest, modifier = modifier)

@Composable
private fun JournalNotice(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(16.dp)) {
        Text(text, style = TmtnType.body, color = Muted)
        if (action != null) TextButton(onClick = onAction) { Text(action, style = TmtnType.label, color = Ink) }
    }
}

private fun shortDate(raw: String): String = completionDate(raw)?.let { "${it.monthValue}월 ${it.dayOfMonth}일" } ?: "날짜 확인 중"

private fun statusLabel(status: String?): String = when (status) {
    "COMPLETED" -> "실천"
    "REST" -> "쉼"
    "INCOMPLETE" -> "미완료"
    else -> "기록 없음"
}
