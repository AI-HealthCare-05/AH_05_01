package com.tmtn.app.ui.cardhome

import com.tmtn.app.ui.common.TmtnHomeHero
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.common.toKoreanDateLabel
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/**
 * 상태전이 정책 신규 화면(B18~B26) — 2026-09-07 홍주님 전달
 * `백엔드전달_상태전이_2026-09-07.zip`(참고/화면/B18~B26.xml) 그대로 옮김.
 *
 * ⚠️ 전부 기존 홈(B01/B01b, CardHomeScreen.kt)과 같은 뼈대 — "비버 · 오늘의 카드" 박스
 * 내용만 다르고, 틈튼지수 요약·최근 7일·댐 카드는 그대로 재사용함(TmtnIndexSummaryCard/
 * RecentSummaryListCard, CardHomeScreen.kt에서 internal 공개로 바꿔둠).
 *
 * ⚠️ 이 파일은 "화면"만 만든 것 — 어느 상태 조합일 때 이 화면들을 실제로 보여줄지
 * (CardHomeStep 추가, CardHomeState의 drawState/todayChallengeState/isTodayRestDay
 * 조합 판단)는 아직 안 붙였음. 이유:
 *   1) REST ↔ GIVE_UP 전환(C23/C27)을 한 번에 처리하는 서버 API가 아직 없음
 *      (cancel_rest_day()는 백엔드엔 있지만 안드로이드 CardHomeApi에 아직 안 뚫림).
 *   2) 카드 미선택 상태의 "포기" 기록(G3, B19) — 서버에 저장할 필드가 아직 없음.
 * 그래서 각 화면은 CardHomeState를 직접 조작하지 않고 콜백 파라미터로 받음 —
 * 위 두 가지가 정해지면 CardHomeFlow.kt에서 이 화면들을 이어붙이면 됨.
 */

@Composable
private fun HomeTopBar(onNotificationsClick: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("틈튼", style = TmtnType.title, color = colors.onSurface)
            Text(
                "알림", style = TmtnType.label, color = colors.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onNotificationsClick)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.Center),
            )
        }
    }
}

@Composable
private fun DateCaption(label: String = LocalDate.now().toKoreanDateLabel()) {
    val colors = LocalTmtnColors.current
    Text(label, style = TmtnType.caption, color = colors.onSurfaceVariant)
}



private data class HomeLink(val text: String, val emphasized: Boolean, val onClick: () -> Unit)

/**
 * 서브 링크 한 줄 — 위계 규칙(상태전이_흐름도 3절): "오늘은 쉬어가기"/"오늘 카드 다시 보기"류는
 * Label 14 Bold·먹색(emphasized=true), "오늘 포기"는 Caption 14 Medium·회색(emphasized=false).
 * 둘 다 14sp 유지(시니어 가독성) — 터치 영역 확보를 위해 vertical padding 12dp.
 */
@Composable
private fun HomeLinkRow(link: HomeLink) {
    TmtnTextButton(link.text, onClick = link.onClick)
}

/** "비버 · 오늘의 카드" 박스 하나 — B18~B26 전부 이 뼈대 위에서 내용만 바뀜. */
@Composable
private fun HomeStateMascotCard(
    illustrationTag: String,
    illustrationDescription: String,
    badgeText: String,
    message: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    links: List<HomeLink>,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TmtnHomeHero(
            image = when {
                badgeText == "쉼" -> com.tmtn.app.R.drawable.beaver_rest
                badgeText == "포기" -> com.tmtn.app.R.drawable.beaver_empty
                badgeText == "중단" -> com.tmtn.app.R.drawable.beaver_tilt
                else -> com.tmtn.app.R.drawable.beaver_card
            },
            description = illustrationTag, status = badgeText, title = message,
        )
        TmtnPrimaryButton(text = primaryLabel, onClick = onPrimaryClick)
        links.forEach { HomeLinkRow(it) }
    }
}

/** 홈 화면 하나(헤더 + 마스코트 카드 + 틈튼지수 요약 + 최근 7일/댐) 공통 뼈대. */
@Composable
private fun HomeStateScreenShell(
    state: CardHomeState,
    onNotificationsClick: () -> Unit,
    onOpenTuntunScore: () -> Unit = {},
    topBanner: (@Composable () -> Unit)? = null,
    // ⚠️ 2026-09-08 QA(N11) 반영: LocalDate.now()(기기 진짜 오늘)를 그대로 써서, 시뮬레이션
    // 날짜와 상단 표시가 어긋났음.
    dateLabel: String = state.displayDateLabel().toKoreanDateLabel(),
    mascotCard: @Composable () -> Unit,
) {
    // ⚠️ 2026-09-07 반영: 상태전이 정책 신규 화면(B18~B26)엔 테스트용 날짜 이동 배너가
    // 없어서, 쉼/포기/중단 상태에서 배너가 통째로 사라져 보였음(QA) - 여기도 공용으로 넣음.
    // 이 화면들(HomeRestNoCardScreen 등)이 scope를 따로 안 받으니 여기서 직접 얻음.
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeTopBar(onNotificationsClick)
        DateCaption(dateLabel)
        DebugDayBanner(state, scope)
        topBanner?.invoke()
        mascotCard()
        TmtnIndexSummaryCard(state, onOpenTuntunScore)
        RecentSummaryListCard(state)
    }
}

/** Figma B18 · 홈 · 카드 미선택 · 쉬어가기 [1-2] */
@Composable
fun HomeRestNoCardScreen(
    state: CardHomeState,
    onNotificationsClick: () -> Unit,
    onOpenTuntunScore: () -> Unit = {},
    onChallengeFromRest: () -> Unit, // "오늘 미션 도전하기" -> C25(쉬어가기 취소)
    onGiveUp: () -> Unit,            // "오늘 포기" -> C27(쉬어가기 -> 포기 전환)
) {
    HomeStateScreenShell(state, onNotificationsClick, onOpenTuntunScore) {
        HomeStateMascotCard(
            illustrationTag = "쉬는 비버",
            illustrationDescription = "그루터기에 기대 눈 감은 비버. 도구는 옆에 가지런히.",
            badgeText = "쉼",
            message = "편하게 쉬어요",
            primaryLabel = "오늘 미션 도전하기",
            onPrimaryClick = onChallengeFromRest,
            links = listOf(
                HomeLink("오늘 포기", emphasized = false, onClick = onGiveUp),
            ),
        )
    }
}

/** Figma B19 · 홈 · 카드 미선택 · 포기 [1-4] */
@Composable
fun HomeGiveUpNoCardScreen(
    state: CardHomeState,
    onNotificationsClick: () -> Unit,
    onOpenTuntunScore: () -> Unit = {},
    onPickCardAgain: () -> Unit, // "오늘 카드 고르기" -> B03(새로 뽑기 - 카드 미선택 상태였으므로)
    onRestInstead: () -> Unit,   // "오늘은 쉬어가기" -> C23
) {
    HomeStateScreenShell(state, onNotificationsClick, onOpenTuntunScore) {
        HomeStateMascotCard(
            illustrationTag = "연장을 내려놓은 비버",
            illustrationDescription = "연장을 내려놓고 하늘을 보는 비버. 지친 표정은 아니다.",
            badgeText = "포기",
            message = "오늘은 여기까지",
            primaryLabel = "오늘 카드 고르기",
            onPrimaryClick = onPickCardAgain,
            links = listOf(
                HomeLink("오늘은 쉬어가기", emphasized = true, onClick = onRestInstead),
            ),
        )
    }
}

/** Figma B20 · 홈 · 카드 뽑음 · 쉬어가기 [2-2] */
@Composable
fun HomeRestCardDrawnScreen(
    state: CardHomeState,
    onNotificationsClick: () -> Unit,
    onOpenTuntunScore: () -> Unit = {},
    onChallengeFromRest: () -> Unit, // "고른 미션 도전하기" -> C25
    onPreviewTodayCard: () -> Unit,  // "오늘 카드 다시 보기" -> B06
    onGiveUp: () -> Unit,            // "오늘 포기" -> C27
) {
    HomeStateScreenShell(state, onNotificationsClick, onOpenTuntunScore) {
        HomeStateMascotCard(
            illustrationTag = "카드를 품고 쉬는 비버",
            illustrationDescription = "뽑은 카드를 가슴에 안고 눈 감은 비버.",
            badgeText = "쉼",
            message = "카드는 남아 있어요",
            primaryLabel = "고른 미션 도전하기",
            onPrimaryClick = onChallengeFromRest,
            links = listOf(
                HomeLink("오늘 카드 다시 보기", emphasized = true, onClick = onPreviewTodayCard),
                HomeLink("오늘 포기", emphasized = false, onClick = onGiveUp),
            ),
        )
    }
}

/** Figma B21 · 홈 · 카드 뽑음 · 포기 [2-4] */
@Composable
fun HomeGiveUpCardDrawnScreen(
    state: CardHomeState,
    onNotificationsClick: () -> Unit,
    onOpenTuntunScore: () -> Unit = {},
    onChallenge: () -> Unit,   // "고른 미션 도전하기" -> C02(타이머 시작 전)
    onRestInstead: () -> Unit, // "오늘은 쉬어가기" -> C23
) {
    HomeStateScreenShell(state, onNotificationsClick, onOpenTuntunScore) {
        HomeStateMascotCard(
            illustrationTag = "카드를 옆에 둔 비버",
            illustrationDescription = "카드를 옆에 두고 먼 곳을 보는 비버.",
            badgeText = "포기",
            message = "카드는 남아 있어요",
            primaryLabel = "고른 미션 도전하기",
            onPrimaryClick = onChallenge,
            links = listOf(
                HomeLink("오늘은 쉬어가기", emphasized = true, onClick = onRestInstead),
            ),
        )
    }
}

/** Figma B22 · 홈 · 측정 중단 · 이어하기 대기 [3-2] */
@Composable
fun HomePausedScreen(
    state: CardHomeState,
    onNotificationsClick: () -> Unit,
    onOpenTuntunScore: () -> Unit = {},
    elapsedLabel: String,       // "6분 20초까지 했어. 이어서 하면 돼." 처럼 실제 경과시간이 들어갈 자리
    onResume: () -> Unit,       // "미션 이어서 하기" -> C03
    onRestInstead: () -> Unit,  // "오늘은 쉬어가기" -> C23
    onGiveUp: () -> Unit,       // "오늘 포기" -> C26
) {
    HomeStateScreenShell(state, onNotificationsClick, onOpenTuntunScore) {
        HomeStateMascotCard(
            illustrationTag = "숨 고르는 비버",
            illustrationDescription = "그루터기에 앉아 숨 고르는 비버. 도구는 내려놓지 않았다.",
            badgeText = "중단",
            message = elapsedLabel,
            primaryLabel = "미션 이어서 하기",
            onPrimaryClick = onResume,
            links = listOf(
                HomeLink("오늘은 쉬어가기", emphasized = true, onClick = onRestInstead),
                HomeLink("오늘 포기", emphasized = false, onClick = onGiveUp),
            ),
        )
    }
}

/** Figma B23 · 홈 · 측정 중 → 쉬어가기 [3-3] */
@Composable
fun HomeRestInProgressScreen(
    state: CardHomeState,
    onNotificationsClick: () -> Unit,
    onOpenTuntunScore: () -> Unit = {},
    onChallengeFromRest: () -> Unit, // "고른 미션 이어서 하기" -> C25
    onPreviewTodayCard: () -> Unit,  // "오늘 카드 다시 보기" -> B06
    onGiveUp: () -> Unit,            // "오늘 포기" -> C27
) {
    HomeStateScreenShell(state, onNotificationsClick, onOpenTuntunScore) {
        HomeStateMascotCard(
            illustrationTag = "카드를 품고 쉬는 비버",
            illustrationDescription = "하던 걸 멈추고 카드를 안은 채 쉬는 비버.",
            badgeText = "쉼",
            message = "잠시 쉬어가요",
            primaryLabel = "고른 미션 이어서 하기",
            onPrimaryClick = onChallengeFromRest,
            links = listOf(
                HomeLink("오늘 카드 다시 보기", emphasized = true, onClick = onPreviewTodayCard),
                HomeLink("오늘 포기", emphasized = false, onClick = onGiveUp),
            ),
        )
    }
}

/** Figma B24 · 홈 · 측정 중 → 포기 [3-4] */
@Composable
fun HomeGiveUpInProgressScreen(
    state: CardHomeState,
    onNotificationsClick: () -> Unit,
    onOpenTuntunScore: () -> Unit = {},
    onRetryChallenge: () -> Unit, // "미션 다시 도전하기" -> C03
    onRestInstead: () -> Unit,    // "오늘은 쉬어가기" -> C23
) {
    HomeStateScreenShell(state, onNotificationsClick, onOpenTuntunScore) {
        HomeStateMascotCard(
            illustrationTag = "연장을 내려놓은 비버",
            illustrationDescription = "연장을 내려놓고 하늘을 보는 비버. 지친 표정은 아니다.",
            badgeText = "포기",
            message = "다시 도전해 볼까요?",
            primaryLabel = "미션 다시 도전하기",
            onPrimaryClick = onRetryChallenge,
            links = listOf(
                HomeLink("오늘은 쉬어가기", emphasized = true, onClick = onRestInstead),
            ),
        )
    }
}

/** Figma B26 · 자정 정산 결과 안내 · 다음 날 첫 진입. */
@Composable
fun HomeNextDayEntryScreen(
    state: CardHomeState,
    onNotificationsClick: () -> Unit,
    onOpenTuntunScore: () -> Unit = {},
    yesterdayDateLabel: String,      // "9월 7일"
    restDaysRemainingLabel: String,  // "2회"
    onPickCard: () -> Unit,          // "오늘의 카드 고르기" -> B03
    onRestInstead: () -> Unit,       // "오늘은 쉬어가기" -> C23
    onGiveUp: () -> Unit,            // "오늘 포기" -> C26
) {
    val colors = LocalTmtnColors.current
    HomeStateScreenShell(
        state = state,
        onNotificationsClick = onNotificationsClick,
        onOpenTuntunScore = onOpenTuntunScore,
        dateLabel = state.displayDateLabel().toKoreanDateLabel(),
        topBanner = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "어제($yesterdayDateLabel)는 미완료로 마무리됐어요",
                    style = TmtnType.label, color = colors.onSurface,
                )
                Text(
                    "연속 기록은 다시 시작해요. 이번 주 쉬어가기 $restDaysRemainingLabel 남았어요.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
                Text(
                    "오늘의 작은 행동부터 다시 쌓아봐요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }
        },
    ) {
        HomeStateMascotCard(
            illustrationTag = "카드를 든 비버",
            illustrationDescription = "카드 세 장을 부채처럼 펴 든 비버.",
            badgeText = "오늘",
            message = "오늘의 카드",
            primaryLabel = "오늘의 카드 고르기",
            onPrimaryClick = onPickCard,
            links = listOf(
                HomeLink("오늘은 쉬어가기", emphasized = true, onClick = onRestInstead),
                HomeLink("오늘 포기", emphasized = false, onClick = onGiveUp),
            ),
        )
    }
}
