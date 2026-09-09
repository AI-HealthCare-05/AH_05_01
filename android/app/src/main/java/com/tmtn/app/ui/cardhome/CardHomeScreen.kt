package com.tmtn.app.ui.cardhome

import com.tmtn.app.ui.common.MockBadge
import com.tmtn.app.ui.common.toKoreanDateLabel
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Figma B01·B01b · 홈 (오늘 카드 미선택/선택됨은 draw_state로 구분) */
@Composable
fun CardHomeScreen(state: CardHomeState, scope: CoroutineScope, onOpenTuntunScore: () -> Unit = {}) {
    val colors = LocalTmtnColors.current
    val isSelected = state.drawState.value == "SELECTED"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("틈튼", style = TmtnType.title, color = colors.onSurface)
            Text(
                "알림", style = TmtnType.label, color = colors.onSurfaceVariant,
                // ⚠️ 2026-09-06 QA(접근성) 반영: 패딩이 아예 없어서 터치 영역이 약 20dp였음.
                modifier = Modifier
                    .clickable { state.step.value = CardHomeStep.NOTIFICATION_INBOX }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.Center),
            )
        }
        Text(state.displayDateLabel().toKoreanDateLabel(), style = TmtnType.caption, color = colors.onSurfaceVariant)

        // ⚠️ 2026-09-07 반영: 상태전이 정책 신규 홈 화면(B18~B26, HomeStateScreens.kt)에는
        // 이 배너가 아예 없어서, 쉼/포기/중단 상태에서 미션을 고르면 배너가 통째로 사라져
        // "비활성화됐다"는 QA로 이어졌음(팀원 계정이 예전 테스트로 쉼/포기 상태에 남아있던
        // 채로 다시 미션을 고르면 그 특수 화면으로 넘어가면서 배너가 사라졌던 것). 공용
        // 함수(DebugDayBanner)로 뽑아서 모든 홈 화면이 같이 쓰게 함.
        DebugDayBanner(state, scope)

        MascotCard(state, isSelected, scope)
        TmtnIndexSummaryCard(state, onOpenTuntunScore)
        RecentSummaryListCard(state)
    }
}

@Composable
private fun MascotCard(state: CardHomeState, isSelected: Boolean, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val isRestDay = state.isTodayRestDay.value
    val isCompleted = state.todayChallengeState.value == "COMPLETED"
    // ⚠️ "중단"으로 끝낸 미션(SKIPPED)도 draw_state는 계속 SELECTED라 isSelected가 true로
    // 남는데, 그대로 두면 "미션 이어하기"가 보여서 다시 시작할 수 있는 것처럼 보임(실제로는
    // 서버가 재시작을 막아 에러가 남). isCompleted 다음으로 먼저 체크해서 우선함.
    //
    // ⚠️ 2026-09-07 반영: 백엔드전달_상태전이 문서 기준으로 "중단"과 "포기"의 이름이
    // 거꾸로 쓰이고 있었음 - 서버 SKIPPED(오늘을 접은 것, 문서의 "포기"·GIVE_UP)를 화면에
    // "중단"이라고 부르고 있었고, 정작 서버 PAUSED(진행값 보존, 문서의 "중단")는 별도
    // 표시가 아예 없이 그냥 "진행 중"으로 뭉뚱그려졌음. 문서 용어에 맞게 정리:
    // SKIPPED → "포기", PAUSED → "중단"(신규).
    val isGivenUp = state.todayChallengeState.value == "SKIPPED"
    val isPaused = state.todayChallengeState.value == "PAUSED"
    // ⚠️ 2026-09-08 QA(N5) 반영: isSelected(카드 확정됨) 하나로만 뱃지를 정해서, 아직
    // "시작하기"도 안 누른 READY 상태까지 "진행 중"으로 잘못 보였음(리포트: "카드를 뽑기만
    // 했는데 뱃지가 진행 중"). READY만 따로 구분 - ACTIVE일 때만 진짜 "진행 중".
    val isReady = state.todayChallengeState.value == "READY"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(88.dp),
            contentAlignment = Alignment.Center,
        ) {
            // ⚠️ 2026-09-06 반영: 홍주님이 전달한 비버 그림 10종을 실제로 붙임(문서
            // ASSETS_배치_전달서_2026-09-06.md 4장 참고). "완료"에 정확히 맞는 전용 포즈는
            // 없어서, 뿌듯함과 가장 가까운 응원 포즈(cheer)를 재사용함 - 선택됨 상태와
            // 그림이 같아지지만, 지금 있는 자산 안에서는 이게 제일 자연스러움.
            Image(
                painter = painterResource(
                    when {
                        isCompleted -> com.tmtn.app.R.drawable.beaver_cheer
                        isGivenUp -> com.tmtn.app.R.drawable.beaver_empty
                        isPaused -> com.tmtn.app.R.drawable.beaver_tilt
                        isSelected -> com.tmtn.app.R.drawable.beaver_cheer
                        isRestDay -> com.tmtn.app.R.drawable.beaver_rest
                        else -> com.tmtn.app.R.drawable.beaver_card
                    },
                ),
                // ⚠️ 2026-09-06 QA(접근성) 반영: contentDescription이 null이라 TalkBack
                // 사용자에게 이 화면 상태를 알려주는 그림 자체가 안 읽혔음.
                contentDescription = when {
                    isCompleted -> "오늘 미션을 완료한 비버"
                    isGivenUp -> "미션을 포기한 비버"
                    isPaused -> "잠시 멈춰서 갸웃하는 비버"
                    isSelected -> "미션을 응원하는 비버"
                    isRestDay -> "쉬고 있는 비버"
                    else -> "카드를 든 비버"
                },
                modifier = Modifier.height(88.dp),
            )
        }

        if (isCompleted) {
            StatusBadge(text = "완료")
        } else if (isGivenUp) {
            // ⚠️ 2026-09-03 리뷰 반영: SKIPPED를 REST(쉼)와 같은 "쉼" 뱃지로 보여주고
            // 있었음. 서버 기준(record_service.py)으로 REST는 연속 기록이 안 끊기고 주 2회
            // 한도가 차감되지만, SKIPPED는 COMPLETED가 아니라서 그대로 INCOMPLETE로 집계되고
            // 연속 기록이 끊김. 홈에서는 "쉼"이라 안심시켜놓고 기록 탭 가면 연속 기록이 끊겨
            // 있는 모순이라, 명확히 구분함.
            //
            // ⚠️ 2026-09-07 반영: 백엔드전달_상태전이 문서 기준 - 이 상태(SKIPPED)는 문서의
            // "포기(GIVE_UP)"에 해당함. 예전엔 여기를 "중단"이라고 불렀는데, 진짜 중단
            // (PAUSED, 진행값 보존)은 따로 표시가 없어서 용어가 서로 바뀌어 있었음.
            StatusBadge(text = "포기")
        } else if (isPaused) {
            StatusBadge(text = "중단")
        } else if (isReady) {
            // ⚠️ 2026-09-08 QA(N5) 반영: 시작 전(READY)에는 "카드 뽑음"으로.
            StatusBadge(text = "카드 뽑음")
        } else if (isSelected) {
            StatusBadge(text = "진행 중")
        } else if (isRestDay) {
            StatusBadge(text = "쉼")
        }

        Text(
            // ⚠️ 2026-09-06 QA(P2) 반영: isSelected/else만 반말이고 나머지는 존댓말이라
            // 한 화면 안에서 말투가 섞여 보였음 - 존댓말로 통일.
            when {
                isCompleted -> "오늘 몫은 다 했어요. 잘했어요!"
                isGivenUp -> "오늘 카드는 여기서 멈췄어요."
                isPaused -> "잠깐 멈춰뒀어요. 이어서 하면 돼요."
                isReady -> "오늘 고른 미션, 시작할 준비가 됐어요."
                isSelected -> "오늘 고른 미션이 기다리고 있어요."
                isRestDay -> "오늘은 쉬어가는 날이에요."
                else -> "안녕하세요! 오늘 카드 세 장 가져왔어요."
            },
            style = TmtnType.bodyLarge, color = colors.onSurface, textAlign = TextAlign.Center,
        )

        TmtnPrimaryButton(
            text = when {
                isCompleted -> "오늘 카드 다시 보기"
                isGivenUp -> "오늘 카드 다시 보기"
                // ⚠️ 2026-09-04 반영: "미션 이어하기"라는 별도 문구 대신 다른 선택된 상태와
                // 똑같이 "오늘 카드 다시 보기"로 통일 - 눌렀을 때 곧장 진행 화면으로 안 들어가고
                // 카드 화면부터 보여주는 걸로 바뀌어서, 문구도 그 결과와 맞춰야 헷갈리지 않음.
                isSelected -> "오늘 카드 다시 보기"
                isRestDay -> "그래도 미션 해볼래요"
                else -> "오늘의 카드 고르기"
            },
            onClick = {
                if (isSelected) {
                    scope.launch { state.continueTodayMission() }
                } else {
                    state.step.value = CardHomeStep.DECK_PICK
                }
            },
        )

        // ⚠️ 오늘 몫을 이미 끝냈으면(완료든 쉬어가기든) "쉬어가기"를 보여줄 이유가 없음 -
        // 이미 끝났는데 쉬겠다는 게 의미상 안 맞음. 그 전(선택만 했거나 아직 안 골랐을 때)에만 노출.
        if (isCompleted) {
            Text(
                "연속 기록 ${state.currentStreak.value}일째 · 오늘도 이어졌어요",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else if (isGivenUp) {
            // 별다른 캡션 없음 - 이미 위 상태 문구로 충분히 설명됨.
        } else if (!isRestDay || isSelected) {
            // ⚠️ 2026-09-06 반영: 캡션 텍스트 한 줄만 클릭 영역이라 좁아서 정확히 그
            // 위를 안 누르면 반응이 없는 것처럼 느껴질 수 있었음. 터치 영역을 넉넉하게 넓힘.
            // ⚠️ 재수정: fillMaxWidth()만 넣고 textAlign을 안 줘서 글자가 왼쪽으로 밀려
            // 보이던 버그 - 원래처럼 가운데 정렬되게 함.
            Text(
                "오늘은 쉬어가기", style = TmtnType.caption, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clickable { scope.launch { state.openRestDaySheet() } },
            )
        } else {
            Text(
                "연속 기록 ${state.currentStreak.value}일째 · 그대로 이어져요",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

// ⚠️ 2026-09-07 반영: 테스트 전용 - 미션 10개를 이어서 테스트하려면 실제로 10일이 걸리니,
// 서버가 인식하는 "오늘"을 하루씩 앞당겨서 바로 다음 미션을 받을 수 있게 함. 디버그
// 빌드에서만 보임(release APK에는 안 보임 + 서버도 PROD면 404로 막아둠 - 이중 안전장치).
// 예전엔 CardHomeScreen(B01/B01b) 안에만 있어서, 상태전이 정책 신규 홈 화면(B18~B26)으로
// 넘어가면 배너가 통째로 사라졌음 - 공용 함수로 뽑아서 모든 홈 화면이 같이 씀.
@Composable
internal fun DebugDayBanner(state: CardHomeState, scope: CoroutineScope) {
    if (!com.tmtn.app.BuildConfig.DEBUG) return
    val colors = LocalTmtnColors.current
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("테스트: 시뮬레이션 오늘 = ${state.debugSimulatedToday.value ?: "-"}", style = TmtnType.caption, color = colors.error)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "초기화", style = TmtnType.caption, color = colors.error,
                modifier = Modifier.clickable { scope.launch { state.resetDebugDay() } },
            )
            Text(
                "다음 날 ›", style = TmtnType.label, color = colors.error,
                modifier = Modifier.clickable { scope.launch { state.advanceDebugDay() } },
            )
        }
    }
}

@Composable
internal fun StatusBadge(text: String) {
    val colors = LocalTmtnColors.current
    Box(
        modifier = Modifier
            .background(colors.secondaryContainer, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text, style = TmtnType.caption, color = colors.onSurface)
    }
}

/**
 * Figma "틈튼지수 요약" 카드.
 * ⚠️ 예전엔 "68"/"보통 구간"/날짜가 전부 하드코딩된 예시값이었음. /tuntun-score/v2를
 * 그대로 써서(참고 탭 CardHomeState.loadTuntunIndexSummary() 참고) 실제 값으로 표시함.
 * 아직 계산할 수 없는 상태(신체정보·운동습관 미입력 등)면 "68" 대신 안내 문구를 보여줌.
 */
@Composable
internal fun TmtnIndexSummaryCard(state: CardHomeState, onOpenTuntunScore: () -> Unit = {}) {
    val colors = LocalTmtnColors.current
    val displayText = state.tuntunIndexDisplayText.value
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("틈튼지수", style = TmtnType.label, color = colors.onSurface)
                if (displayText != null && state.tuntunIndexIsMock.value) {
                    MockBadge()
                }
            }
            // ⚠️ 2026-09-08 QA 반영: TODO만 남긴 채 클릭 핸들러가 아예 없었음(그냥 Text) -
            // 틈튼지수 탭으로 전환하는 클릭 영역 추가.
            Text(
                "자세히 ›", style = TmtnType.caption, color = colors.onSurfaceVariant,
                modifier = Modifier.clickable { onOpenTuntunScore() }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .wrapContentSize(Alignment.CenterEnd)
                    .padding(4.dp),
            )
        }
        if (state.tuntunIndexLoadFailed.value) {
            // ⚠️ PR #12 리뷰(P1) 반영: 네트워크 실패를 "정보를 입력하세요"로 잘못 안내하던
            // 문제 - 이미 다 입력한 사람이 오프라인이면 입력하라는 말을 들었음.
            Text("불러오지 못했어요 · 네트워크를 확인해 주세요", style = TmtnType.body, color = colors.onSurfaceVariant)
        } else if (displayText == null) {
            Text(
                "아직 계산할 수 없어요 · 신체정보나 운동습관을 입력해 보세요",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )
        } else {
            // ⚠️ 2026-09-09 반영: 실모델 전환 - 기존 "관심/보통/양호" 구간 막대는 새 계약에
            // 없는 개념이라(bandLabel 항상 null) 제거함. 서버가 조립한 문구를 그대로 크게
            // 보여줌.
            Text(displayText, style = TmtnType.display, color = colors.onSurface)
            Text(
                "동일 성별·연령대 참고 표본 대비 위치 · 비진단용 참고 정보",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Figma "최근 7일" + "댐" 목록 카드.
 * ⚠️ 예전엔 "최근 7일" 점 7개가 listOf(true, true, false, true, true, false, null) 하드코딩
 * 예시 패턴으로 항상 똑같이 표시됐음. 기록 탭과 같은 주간 리포트 API(CardHomeState.
 * loadRecentWeek() 참고)를 그대로 써서 실제 최근 7일 상태로 표시함. "댐"은 실제 GET
 * /companion 데이터를 그대로 씀.
 */
@Composable
internal fun RecentSummaryListCard(state: CardHomeState) {
    val colors = LocalTmtnColors.current
    val recentWeek = state.recentWeek.value
    val completedCount = recentWeek.count { it.status == "COMPLETED" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("최근 7일", style = TmtnType.label, color = colors.onSurface)
                Text(
                    // ⚠️ PR #12 리뷰(P1) 반영: 실패 시 빈 리스트라 "0일 실천"이라고 잘못
                    // 말하던 문제 - 5일 실천한 사람에게 0일이라고 안내됐음.
                    if (state.recentWeekLoadFailed.value) "불러오지 못했어요" else "이번 주 ${completedCount}일 실천했어요",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val today = java.time.LocalDate.now().toString()
                recentWeek.forEach { day -> DayDot(status = day.status, isToday = day.date == today) }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("댐", style = TmtnType.label, color = colors.onSurface)
                val stage = state.companionStage.value
                val needed = state.companionMaterialsNeeded.value
                val nextLabel = state.companionNextStageLabel.value
                Text(
                    if (nextLabel != null && needed != null) {
                        "$stage 단계 · $nextLabel · 다음까지 ${needed}개"
                    } else {
                        "$stage 단계"
                    },
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }
            // ⚠️ 이 행 오른쪽에 빈 공간이 있길래, 오행별 재료 개수를 여기 보여주기로 함.
            // "댐" 탭(G01)에서 이미 쓰는 companion.materials를 홈에서도 재사용 - 새 API 없음.
            if (state.companionMaterials.value.isNotEmpty()) {
                // ⚠️ 2026-09-08 QA 반영: 댐 탭과 같은 문제(가로 스크롤 없음) 예방 차원에서
                // 여기도 같이 추가 - 작은 화면·큰 글씨 모드에서 5개가 다 안 들어갈 수 있음.
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MATERIAL_NAMES.keys.forEach { element ->
                        val count = state.companionMaterials.value.firstOrNull { it.element == element }?.count ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            MaterialIcon(element = element, size = 18.dp)
                            Text("$count", style = TmtnType.caption, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDot(status: String, isToday: Boolean) {
    val colors = LocalTmtnColors.current
    // ⚠️ 2026-09-04 QA 반영: 가입 이전 날짜("BEFORE_SIGNUP")까지 이 자리에 올 수 있게 됨
    // (record_service.py 참고) - "미완료"처럼 테두리를 그리면 마치 그날 안 한 것처럼
    // 보이니, 아예 안 그리고 빈 자리로만 남김.
    if (status == "BEFORE_SIGNUP") {
        Box(modifier = Modifier.size(10.dp))
        return
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            // ⚠️ 예전엔 done==null(=예시 데이터의 마지막 자리)일 때만 "오늘" 링이 보였는데,
            // 이제 실제 데이터는 최근 7일이 전부 실제 상태(COMPLETED/INCOMPLETE/REST)를 가져서
            // null인 경우가 없음. MonthlyCalendarScreen.kt와 같은 방식으로 "오늘"을 상태와
            // 무관하게 바깥 링으로 항상 같이 표시함.
            .then(if (isToday) Modifier.border(2.dp, colors.secondary, CircleShape).padding(2.dp) else Modifier)
            .then(
                when (status) {
                    "COMPLETED" -> Modifier.background(colors.onSurface, CircleShape)
                    "REST" -> Modifier.background(colors.disabledContainer, CircleShape).border(1.dp, colors.onSurface, CircleShape)
                    else -> Modifier.border(1.5.dp, colors.outline, CircleShape) // INCOMPLETE
                },
            ),
    )
}
