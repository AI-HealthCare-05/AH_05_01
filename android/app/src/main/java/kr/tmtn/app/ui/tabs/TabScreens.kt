package kr.tmtn.app.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.tmtn.app.designsystem.*
import kr.tmtn.app.domain.ml.ModelRegistry
import kr.tmtn.app.domain.ml.ModelResult
import kr.tmtn.app.ui.TmtnDate
import kr.tmtn.app.ui.TodayViewModel
import kr.tmtn.app.ui.home.IndexCard

/* ------------------------------------------------------------- 기록 탭 */

@Composable
fun RecordScreen(today: TodayViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmtnTopBar("기록")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (today.records.isEmpty()) {
                NoteBox(title = "아직 기록이 없어요", body = "오늘의 카드를 한 장 끝내면 여기에 쌓여요.")
            } else {
                today.records.forEach { r ->
                    TmtnCardBox {
                        Row {
                            Text(TmtnDate.label(r.date), style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            StatusBadge(
                                if (r.measuredByModel) BadgeState.Running else BadgeState.Done,
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
            }
            Spacer(Modifier.height(24.dp))
        }
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

/* ------------------------------------------------------------- 참고 탭 */

@Composable
fun ReferenceScreen(today: TodayViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmtnTopBar("참고")
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

/* ------------------------------------------------------------- 마이 탭 */

@Composable
fun MyPageScreen(today: TodayViewModel, onLoggedOut: () -> Unit) {
    val p = today.profile
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmtnTopBar("마이")
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

            NoteBox(
                body = "주민등록번호와 전체 생년월일은 받지 않습니다. 위치는 거리 미션을 할 때만 쓰고 기록으로 남기지 않습니다.",
            )

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
