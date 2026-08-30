package kr.tmtn.app.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.tmtn.app.designsystem.*
import kr.tmtn.app.domain.ml.ModelRegistry
import kr.tmtn.app.domain.model.DayStatus
import kr.tmtn.app.domain.model.REST_PER_WEEK
import kr.tmtn.app.domain.ml.ModelResult
import kr.tmtn.app.ui.TmtnDate
import kr.tmtn.app.ui.TodayViewModel
import kr.tmtn.app.ui.home.IndexCard

/* ------------------------------------------------------------- 기록 탭 */

/**
 * D01 · 기록 · 월 캘린더.
 *
 * 계층은 **달력 → 연속 기록 → 그 달의 기록 목록** 순이다.
 * 달력이 이 화면의 주인공이므로 맨 위에 두고, 목록은 딸린 정보로 아래에 둔다.
 */
@Composable
fun RecordScreen(today: TodayViewModel) {
    val now = remember { java.time.LocalDate.now() }
    var year by remember { mutableIntStateOf(now.year) }
    var month by remember { mutableIntStateOf(now.monthValue) }
    var selected by remember { mutableStateOf<String?>(null) }

    // D03 · 하루 상세 시트
    selected?.let { key ->
        DayDetailSheet(today, key) { selected = null }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmtnTopBar("기록")
        Column(
            Modifier.padding(horizontal = TmtnSpace.ScreenMargin),
            verticalArrangement = Arrangement.spacedBy(TmtnSpace.S16),
        ) {
            MonthCalendar(
                today = today,
                year = year, month = month,
                onPrev = {
                    val d = java.time.LocalDate.of(year, month, 1).minusMonths(1)
                    year = d.year; month = d.monthValue
                },
                onNext = {
                    val d = java.time.LocalDate.of(year, month, 1).plusMonths(1)
                    year = d.year; month = d.monthValue
                },
                onPick = { selected = it },
            )

            StreakCard(today)

            /* ── 그 달의 기록 ─────────────────────────────── */
            val monthRecords = today.records.filter {
                TmtnDate.monthOf(it.date) == month && it.date.startsWith("$year-")
            }
            if (monthRecords.isEmpty()) {
                // D04 · 기록 없음
                NoteBox(
                    title = "이 달에는 아직 기록이 없어요",
                    body = "카드를 한 장 끝내면 여기에 쌓여요.",
                )
            } else {
                Text("${month}월의 기록", style = TmtnText.Label, color = TmtnColor.OnSurface)
                monthRecords.sortedByDescending { it.date }.forEach { r -> RecordRow(r) }
            }
            Spacer(Modifier.height(TmtnSpace.S24))
        }
    }
}

@Composable
private fun MonthCalendar(
    today: TodayViewModel,
    year: Int,
    month: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: (String) -> Unit,
) {
    val grid = remember(year, month) { TmtnDate.monthGrid(year, month) }

    TmtnCardBox {
        // 이 줄의 주인공은 "몇 월" 이다. 이동 버튼은 옆에서 거드는 정도로 둔다.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TmtnQuietButton("이전", fillWidth = false, onClick = onPrev)
            Text(
                "${year}년 ${month}월",
                style = TmtnText.Title, color = TmtnColor.OnSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TmtnQuietButton("다음", fillWidth = false, onClick = onNext)
        }

        // 월요일 시작
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TmtnDate.weekdayHeaders.forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(it, style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
                }
            }
        }

        grid.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { key ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CalendarDayCell(
                            day = TmtnDate.dayOf(key),
                            status = today.statusOf(key),
                            isToday = key == today.dateKey,
                            inCurrentMonth = TmtnDate.monthOf(key) == month,
                            onClick = { onPick(key) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(TmtnSpace.S4))
        CalendarLegend()
    }
}

/** D05 · 연속 기록 · 쉼 규칙 */
@Composable
private fun StreakCard(today: TodayViewModel) {
    TmtnCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("연속 기록", style = TmtnText.Label, color = TmtnColor.OnSurface)
                Text(
                    "쉼으로 표시한 날은 끊기지 않아요",
                    style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
                )
            }
            Text("${today.streak()}일", style = TmtnText.Title, color = TmtnColor.OnSurface)
        }
        StatRow("이번 주 쉼", "${today.restUsedThisWeek()} / $REST_PER_WEEK")
    }
}

/** D03 · 하루 상세 시트 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(today: TodayViewModel, dateKey: String, onClose: () -> Unit) {
    val records = today.records.filter { it.date == dateKey }
    val status = today.statusOf(dateKey)

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = TmtnColor.Background,
    ) {
        Column(
            Modifier.padding(horizontal = TmtnSpace.ScreenMargin).padding(bottom = TmtnSpace.S32),
            verticalArrangement = Arrangement.spacedBy(TmtnSpace.S12),
        ) {
            Text(TmtnDate.weekdayLabel(dateKey), style = TmtnText.Title, color = TmtnColor.OnSurface)

            when (status) {
                DayStatus.DONE -> records.forEach { RecordRow(it) }
                DayStatus.REST -> NoteBox(
                    title = "쉬어간 날이에요",
                    body = "직접 고른 쉼이라 연속 기록은 이어집니다.",
                )
                DayStatus.MISSED -> NoteBox(
                    title = "기록이 없는 날이에요",
                    body = "지나간 날은 되돌려 기록할 수 없어요. 오늘부터 다시 쌓으면 됩니다.",
                )
                DayStatus.FUTURE -> NoteBox(body = "아직 오지 않은 날이에요.")
            }
        }
    }
}

@Composable
private fun RecordRow(r: kr.tmtn.app.domain.model.DailyRecord) {
    TmtnCardBox {
        Row {
            Text(TmtnDate.label(r.date), style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
            Spacer(Modifier.weight(1f))
            // 둘 다 이미 완료된 기록이다. 측정 "방식" 이지 상태가 아니므로
            // 진행중(주황)·완료(먹색) 칩을 쓰지 않는다. 구분은 글자가 한다.
            StatusBadge(
                BadgeState.Info,
                if (r.measuredByModel) "자동 측정" else "직접 확인",
            )
        }
        Text(r.title, style = TmtnText.Label, color = TmtnColor.OnSurface)
        Text(
            "${r.achieved}${r.unit} · 목표 ${r.targetNumber}${r.unit} · ${r.completedAtLabel}",
            style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
        )
        Text(
            "${r.axis.accessibleText()} · ${r.rewardName} 1개",
            style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
        )
    }
}

/* --------------------------------------------------------------- 댐 탭 */

@Composable
fun DamScreen(today: TodayViewModel) {
    val counts = today.materialCounts()
    val total = today.records.size

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmtnTopBar("댐")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            ImageSlot(
                tag = "일러스트 자리 · 비버의 댐",
                title = "댐 그림이 들어올 자리",
                desc = "단계별 댐 일러스트는 아직 준비되지 않아 도형으로 표시했습니다.",
                height = 180.dp,
            )

            TmtnCardBox {
                Text("지금까지 모은 재료 ${total}개", style = TmtnText.Title, color = TmtnColor.OnSurface)
                Text("${total / 5 + 1}단계 · 다음 단계까지 ${5 - (total % 5)}개", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
                MeterBar((total % 5) / 5f)
            }

            if (counts.isEmpty()) {
                NoteBox(body = "미션을 하나 끝낼 때마다 재료가 하나씩 쌓여요.")
            } else {
                TmtnCardBox {
                    Text("재료", style = TmtnText.Label, color = TmtnColor.OnSurface)
                    counts.forEach { (name, n) -> StatRow(name, "${n}개") }
                }
            }

            NoteBox(
                body = "댐은 실천을 모아 보는 곳이에요. 건강 수치나 틈튼지수와는 합산하지 않습니다.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* --------------------------------------------------------- 틈튼지수 탭 */

@Composable
fun ReferenceScreen(today: TodayViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmtnTopBar("틈튼지수")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            IndexCard(today.indexState, onDetail = { })

            when (val st = today.indexState) {
                is ModelResult.Ready -> {
                    TmtnCardBox {
                        Text("무엇이 점수에 영향을 줬나요", style = TmtnText.Label, color = TmtnColor.OnSurface)
                        st.value.factors.forEach { f ->
                            StatRow(f.name, if (f.contribution >= 0) "올림" else "내림")
                            Text(f.explanation, style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
                        }
                    }
                }
                else -> Unit
            }

            WaistCard(today)

            TmtnCardBox {
                // [DEMO-MODEL] "샘플" 대신 실제 버전이 뜬다. 지우지 말 것.
                Text("연결된 모델", style = TmtnText.Label, color = TmtnColor.OnSurface)
                StatRow("허리둘레 추정", ModelRegistry.waistEstimator.info.let { if (it.isPlaceholder) "샘플" else it.version })
                StatRow("행동 인식", ModelRegistry.activityRecognizer.info.let { if (it.isPlaceholder) "샘플" else it.version })
                StatRow("틈튼지수", ModelRegistry.indexScorer.info.let { if (it.isPlaceholder) "샘플" else it.version })
            }

            NoteBox(
                title = "비진단용 참고 정보입니다",
                body = "진료를 대신하지 않습니다. 점수가 바뀐 것은 입력값이 바뀌어 다시 계산된 결과이며, " +
                    "건강이 좋아지거나 나빠졌다는 뜻이 아닙니다. 걱정되는 점이 있으면 의료진과 상담해 주세요.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ---------------------------------------------------------- 내 정보 탭 */

@Composable
fun MyPageScreen(today: TodayViewModel, onLoggedOut: () -> Unit) {
    val p = today.profile
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmtnTopBar("내 정보")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            TmtnCardBox {
                Text(p.displayName, style = TmtnText.Title, color = TmtnColor.OnSurface)
                StatRow("출생연도", if (p.birthYear > 0) "${p.birthYear}년" else "-")
                StatRow("키 · 몸무게", "%.0f cm · %.0f kg".format(p.heightCm, p.weightKg))
                StatRow("근력운동", p.strengthDaysWeek?.let { if (it == 0) "안 함" else if (it >= 5) "주 5회+" else "주 ${it}회" } ?: "입력 안 함")
                StatRow("근력 강도", p.strengthIntensity?.label ?: "입력 안 함")
                StatRow("유산소 · 저강도", p.aerobicLightMinWeek?.let { "주 ${it}분" } ?: "입력 안 함")
                StatRow("유산소 · 중강도", p.aerobicModerateMinWeek?.let { "주 ${it}분" } ?: "입력 안 함")
                StatRow("유산소 · 고강도", p.aerobicVigorousMinWeek?.let { "주 ${it}분" } ?: "입력 안 함")
            }

            // [DEMO] 데모 단계 전용. 서버가 붙으면 지운다.
            TmtnOutlinedButton("데모 데이터 초기화") { today.resetDemo() }
            TmtnQuietButton("로그아웃") {
                today.logout()
                onLoggedOut()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ------------------------------------------------- 허리둘레 카드 */

/**
 * 허리둘레는 **모델이 추정한 값 하나뿐**이다.
 * 앱은 줄자로 잰 값을 아예 받지 않는다 — 계약상 잰 값은 모델 입력으로 쓸 수 없어서
 * (`measured_waist_as_disease_input: prohibited`) 화면에 같이 두면 오해만 생기기 때문이다.
 */
@Composable
private fun WaistCard(today: TodayViewModel) {
    TmtnCardBox {
        Text("허리둘레 (추정)", style = TmtnText.Label, color = TmtnColor.OnSurface)

        when (val state = today.waistState) {
            is ModelResult.Ready -> {
                val e = state.value
                Text("약 %.0f cm".format(e.waistCm), style = TmtnText.Title, color = TmtnColor.OnSurface)
                Text(
                    "추정 범위 %.0f~%.0f cm · 입력 %s".format(e.lowCm, e.highCm, e.tier.name),
                    style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
                )
                Text(
                    "키·몸무게·활동량으로 추정한 값이에요. 줄자로 잰 값은 따로 받지 않습니다.",
                    style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
                )
                StatRow("추정기 버전", e.estimatorVersion)
                if (state.info.isPlaceholder) {
                    Text("샘플 값이에요 — ${state.info.note}", style = TmtnText.Caption, color = TmtnColor.Wood)
                }
            }
            is ModelResult.NotReady -> NoteBox(body = state.reason)
            is ModelResult.UnsupportedPopulation ->
                NoteBox(title = "지금은 추정하지 않아요", body = state.reason)
            is ModelResult.Failed ->
                NoteBox(tone = NoteTone.Warning, title = "추정에 실패했어요", body = state.reason)
            null -> NoteBox(body = "키·몸무게·성별을 넣으면 허리둘레를 추정해 드려요.")
        }
    }
}
