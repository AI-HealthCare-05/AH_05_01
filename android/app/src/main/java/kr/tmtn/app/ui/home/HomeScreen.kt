package kr.tmtn.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kr.tmtn.app.designsystem.*
import kr.tmtn.app.domain.ml.IndexBand
import kr.tmtn.app.domain.model.DayStatus
import kr.tmtn.app.domain.ml.ModelResult
import kr.tmtn.app.domain.ml.TmtnIndexResult
import kr.tmtn.app.ui.TmtnDate
import kr.tmtn.app.ui.TodayViewModel
import kr.tmtn.app.ui.nav.Route

@Composable
fun HomeScreen(today: TodayViewModel, nav: NavHostController) {
    val picked = today.picked
    val done = today.isDoneToday()
    var showRestConfirm by remember { mutableStateOf(false) }

    // B16 · 쉬어가기 확인. 쉼은 **직접 누른 날에만** 기록되므로 한 번 더 묻는다.
    if (showRestConfirm) {
        RestConfirmDialog(
            left = today.restLeftThisWeek(),
            onDismiss = { showRestConfirm = false },
            onConfirm = {
                today.markRestToday()
                showRestConfirm = false
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TmtnTopBar(title = "틈튼", actionLabel = "알림", onAction = { })

        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                TmtnDate.weekdayLabel(today.dateKey),
                style = TmtnText.Caption,
                color = TmtnColor.OnSurfaceVariant,
            )

            RecentWeekCard(today)
            DamSummaryCard(today, onOpen = { nav.navigate(Route.DAM) })

            /* ── 비버 동반 패널 ─────────────────────────────── */
            TmtnCardBox(background = TmtnColor.Surface, padding = 16.dp) {
                ImageSlot(
                    tag = "일러스트 자리 · 카드를 든 비버",
                    title = "브랜드 비버 에셋이 들어올 자리",
                    desc = "원본 색 유지 · 96px 미만 축소 금지.\n아직 준비되지 않아 도형으로 표시했습니다.",
                    height = 140.dp,
                )
                Spacer(Modifier.height(4.dp))

                when {
                    done -> {
                        StatusBadge(BadgeState.Done, "오늘 완료")
                        Text("오늘 할 만큼 했어. 내일 또 보자.", style = TmtnText.Title, color = TmtnColor.OnSurface)
                        TmtnTonalButton("오늘 카드 다시 보기") { nav.navigate(Route.CARD_FRONT) }
                    }
                    // B17 · 홈 · 오늘은 쉼
                    today.isRestToday -> {
                        StatusBadge(BadgeState.Info, "오늘은 쉼")
                        Text("오늘은 쉬어 가기로 했지. 그것도 잘한 거야.", style = TmtnText.Title, color = TmtnColor.OnSurface)
                        Text(
                            "연속 기록은 그대로 이어져. 내일 다시 만나자.",
                            style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant,
                        )
                        TmtnQuietButton("쉼 취소하기") { today.undoRestToday() }
                    }
                    picked != null -> {
                        StatusBadge(BadgeState.Running, "진행 중")
                        Text("${picked.oneLine} 같이 해보자.", style = TmtnText.Title, color = TmtnColor.OnSurface)
                        TmtnFilledButton("미션 이어하기", onClick = { nav.navigate(Route.CARD_FRONT) })
                        RestLink(today) { showRestConfirm = true }
                    }
                    else -> {
                        Text(
                            "안녕 ${today.profile.displayName}야. 오늘 카드 세 장 가져왔어.",
                            style = TmtnText.Title, color = TmtnColor.OnSurface,
                        )
                        TmtnFilledButton("오늘의 카드 고르기", onClick = { nav.navigate(Route.CARD_PICK) })
                        RestLink(today) { showRestConfirm = true }
                    }
                }
            }

            IndexCard(today.indexState, onDetail = { nav.navigate(Route.REFERENCE) })

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 쉬어가기 진입점. 주 버튼과 나란히 두지 않고 **한 단계 낮은 무게**로 둔다 —
 * 오늘의 주된 행동은 어디까지나 카드를 고르는 것이다.
 * 이번 주 몫을 다 썼으면 누를 수 없게 하고 이유를 밝힌다.
 */
@Composable
private fun RestLink(today: TodayViewModel, onClick: () -> Unit) {
    val left = today.restLeftThisWeek()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (left > 0) {
            TmtnQuietButton("오늘은 쉬어가기", onClick = onClick)
        } else {
            Text(
                "이번 주 쉼을 다 썼어요",
                style = TmtnText.Label,
                color = TmtnColor.OnDisabled,
            )
        }
        Text(
            if (left > 0) "이번 주 ${left}번 남음" else "다음 주 월요일에 다시 채워져요",
            style = TmtnText.Caption,
            color = TmtnColor.OnSurfaceVariant,
        )
    }
}

/** B16 · 홈 · 쉬어가기 확인 */
@Composable
private fun RestConfirmDialog(left: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TmtnColor.Background,
        shape = TmtnShape.Sheet,
        title = { Text("오늘은 쉬어갈까요?", style = TmtnText.Title, color = TmtnColor.OnSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "쉼으로 표시하면 연속 기록이 끊기지 않아요.",
                    style = TmtnText.Body, color = TmtnColor.OnSurface,
                )
                Text(
                    "이번 주에 ${left}번 쉴 수 있어요. 쉼은 월요일에 다시 채워집니다.",
                    style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TmtnFilledButton("오늘 쉬어가기", onClick = onConfirm, modifier = Modifier.width(160.dp))
        },
        dismissButton = {
            TmtnQuietButton("아니요", fillWidth = false, onClick = onDismiss)
        },
    )
}

@Composable
private fun RecentWeekCard(today: TodayViewModel) {
    // 홈은 이번 주(월~일)를 보여 준다. 기록 탭의 달력과 주 경계가 같아야 헷갈리지 않는다.
    val days = TmtnDate.thisWeek()
    val count = days.count { today.statusOf(it) == DayStatus.DONE }
    val streak = today.streak()

    TmtnCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("이번 주", style = TmtnText.Label, color = TmtnColor.OnSurface)
                Text(
                    if (streak > 0) "${count}일 실천 · 연속 ${streak}일" else "${count}일 실천했어요",
                    style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEach { d ->
                    WeekDot(today.statusOf(d), isToday = d == today.dateKey)
                }
            }
        }
    }
}

/**
 * 주간 점 하나. 달력과 같은 규칙으로 읽히도록 **실천은 채우고, 쉼은 실선 테두리**로 둔다.
 * 크기가 작아 점선까지는 표현하지 못하므로 미완료는 옅은 채움으로 둔다 —
 * 정확한 네 상태 구분은 기록 탭 달력이 맡는다.
 */
@Composable
private fun WeekDot(status: DayStatus, isToday: Boolean) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(
                when (status) {
                    DayStatus.DONE -> TmtnColor.Primary
                    DayStatus.REST -> TmtnColor.DisabledContainer
                    else -> TmtnColor.OutlineVariant
                },
            )
            .then(
                when {
                    isToday -> Modifier.border(2.dp, TmtnColor.Secondary, CircleShape)
                    status == DayStatus.REST -> Modifier.border(1.5.dp, TmtnColor.OnSurface, CircleShape)
                    else -> Modifier
                },
            ),
    )
}

@Composable
private fun DamSummaryCard(today: TodayViewModel, onOpen: () -> Unit) {
    val total = today.records.size
    val stage = total / 5 + 1
    val toNext = 5 - (total % 5)

    TmtnCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("댐", style = TmtnText.Label, color = TmtnColor.OnSurface)
                Text("${stage}단계 · 다음까지 ${toNext}개", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
            }
            TmtnQuietButton("보러 가기", fillWidth = false, onClick = onOpen)
        }
    }
}

@Composable
fun IndexCard(state: ModelResult<TmtnIndexResult>, onDetail: () -> Unit) {
    TmtnCardBox {
        SectionHeader("틈튼지수", trailing = "자세히", onTrailing = onDetail)

        when (state) {
            is ModelResult.Ready -> {
                val r = state.value
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${r.score}", style = TmtnText.Display, color = TmtnColor.OnSurface)
                    Spacer(Modifier.width(10.dp))
                    StatusBadge(BadgeState.Info, r.band.label)
                }
                MeterBar(
                    progress = r.score / 100f,
                    track = when (r.band) {
                        IndexBand.LOW -> TmtnColor.SecondaryContainer
                        IndexBand.NORMAL -> TmtnColor.OutlineVariant
                        IndexBand.WATCH -> TmtnColor.WoodContainer
                    },
                )
                Text(
                    "${r.windowLabel} · ${r.disclaimer}",
                    style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
                )
                // [DEMO-MODEL] 진짜 모델을 넣고 ModelSlot.info.isPlaceholder = false 로 두면
                // 저절로 사라진다. 코드를 지우지 말 것.
                if (state.info.isPlaceholder) {
                    Text(
                        "샘플 값이에요 — ${state.info.note}",
                        style = TmtnText.Caption, color = TmtnColor.Wood,
                    )
                }
            }
            is ModelResult.NotReady -> NoteBox(body = state.reason)
            is ModelResult.UnsupportedPopulation ->
                NoteBox(title = "지금은 참고 정보를 보여드리지 않아요", body = state.reason)
            is ModelResult.Failed -> NoteBox(tone = NoteTone.Warning, title = "지금은 계산할 수 없어요", body = state.reason)
        }
    }
}
