package kr.tmtn.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kr.tmtn.app.designsystem.*
import kr.tmtn.app.domain.ml.IndexBand
import kr.tmtn.app.domain.ml.ModelResult
import kr.tmtn.app.domain.ml.TmtnIndexResult
import kr.tmtn.app.ui.TmtnDate
import kr.tmtn.app.ui.TodayViewModel
import kr.tmtn.app.ui.nav.Route

@Composable
fun HomeScreen(today: TodayViewModel, nav: NavHostController) {
    val picked = today.picked
    val done = today.isDoneToday()

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
                    picked != null -> {
                        StatusBadge(BadgeState.Running, "진행 중")
                        Text("${picked.oneLine} 같이 해보자.", style = TmtnText.Title, color = TmtnColor.OnSurface)
                        TmtnFilledButton("미션 이어하기", onClick = { nav.navigate(Route.CARD_FRONT) })
                    }
                    else -> {
                        Text(
                            "안녕 ${today.profile.displayName}야. 오늘 카드 세 장 가져왔어.",
                            style = TmtnText.Title, color = TmtnColor.OnSurface,
                        )
                        TmtnFilledButton("오늘의 카드 고르기", onClick = { nav.navigate(Route.CARD_PICK) })
                    }
                }
            }

            IndexCard(today.indexState, onDetail = { nav.navigate(Route.REFERENCE) })

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RecentWeekCard(today: TodayViewModel) {
    val days = TmtnDate.lastDays(7)
    val doneDays = today.records.map { it.date }.toSet()
    val count = days.count { doneDays.contains(it) }

    TmtnCardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("최근 7일", style = TmtnText.Label, color = TmtnColor.OnSurface)
                Text("이번 주 ${count}일 실천했어요", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEach { d ->
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (doneDays.contains(d)) TmtnColor.Primary else TmtnColor.OutlineVariant),
                    )
                }
            }
        }
    }
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
            TmtnQuietButton("보러 가기", onClick = onOpen, modifier = Modifier.width(110.dp))
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
                    StatusBadge(BadgeState.Rest, r.band.label)
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
