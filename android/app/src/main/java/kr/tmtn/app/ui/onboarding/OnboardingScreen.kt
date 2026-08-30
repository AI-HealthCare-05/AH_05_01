package kr.tmtn.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.tmtn.app.designsystem.*
import kr.tmtn.app.domain.ml.AerobicBand
import kr.tmtn.app.domain.ml.PregnancyStatus
import kr.tmtn.app.domain.ml.SexCode
import kr.tmtn.app.domain.ml.StrengthIntensity
import kr.tmtn.app.domain.model.UserProfile

/**
 * 온보딩 — **피그마 A07 · A08 두 단계 그대로.**
 *
 * 1단계 A07 기본 정보 : 이름·닉네임(선택) / 생년월(연·월만) / 성별 / 키·몸무게
 * 2단계 A08 운동 정보 : 근력 주 횟수 + 강도 / 유산소 강도별 주당 시간(저·중·고)
 *
 * 유산소를 "강도별 주당 분" 으로 받는 이유 —
 * 계약서의 파생 피처 공식이 `moderate_min_week + 2 × vigorous_min_week` 라서,
 * 이 화면이 그 값을 **그대로** 만들어 준다. 일수×1회시간으로 받으면 근사식이 된다.
 */
@Composable
fun OnboardingScreen(initial: UserProfile, onDone: (UserProfile) -> Unit) {
    var step by remember { mutableIntStateOf(if (initial.basicComplete) 2 else 1) }
    var p by remember { mutableStateOf(initial) }

    Column(Modifier.fillMaxSize()) {
        TmtnTopBar("필수 입력", onBack = if (step == 2) ({ step = 1 }) else null)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StepBar(step)
            Text("$step / 2단계 · 모두 필요한 값이에요", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)

            if (step == 1) {
                BasicStep(p) { p = it }
            } else {
                ExerciseStep(p) { p = it }
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (step == 1) {
                TmtnFilledButton(
                    text = "다음",
                    enabled = p.basicComplete,
                    disabledReason = "생년월·성별·키·몸무게까지 채우면 다음으로 갈 수 있어요.",
                    onClick = { step = 2 },
                )
            } else {
                TmtnFilledButton(
                    text = "완료하고 시작하기",
                    enabled = p.exerciseComplete,
                    disabledReason = "근력 횟수와 강도, 그리고 유산소 세 가지 강도를 모두 채워 주세요.",
                    onClick = { onDone(p) },
                )
            }
        }
    }
}

/* ---------------------------------------------------- 1단계 · 기본 정보 */

@Composable
private fun BasicStep(p: UserProfile, set: (UserProfile) -> Unit) {
    Text("시작하기 전에\n몇 가지만 알려 주세요", style = TmtnText.Headline, color = TmtnColor.OnSurface)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            Field("이름", p.name, KeyboardType.Text, "김홍주") { set(p.copy(name = it)) }
        }
        Box(Modifier.weight(1f)) {
            Field("닉네임 (선택)", p.nickname, KeyboardType.Text, "홍주") { set(p.copy(nickname = it)) }
        }
    }
    Hint("닉네임을 비우면 이름을 그대로 씁니다. 화면에는 '${p.displayName}' 로 보여요.")

    SectionRow("생년월일", "연·월만")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            Field("연도", if (p.birthYear > 0) p.birthYear.toString() else "", KeyboardType.Number, "1990") {
                set(p.copy(birthYear = it.filter(Char::isDigit).take(4).toIntOrNull() ?: 0))
            }
        }
        Box(Modifier.weight(1f)) {
            Field("월", if (p.birthMonth > 0) p.birthMonth.toString() else "", KeyboardType.Number, "3") {
                set(p.copy(birthMonth = it.filter(Char::isDigit).take(2).toIntOrNull() ?: 0))
            }
        }
    }
    Hint("정확한 날짜는 받지 않습니다. 연·월만 있으면 충분해요.")

    SectionRow("성별", "또래 참고 범위용")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PickChip("남성", p.sexCode == SexCode.MALE) { set(p.copy(sexCode = SexCode.MALE)) }
        PickChip("여성", p.sexCode == SexCode.FEMALE) { set(p.copy(sexCode = SexCode.FEMALE)) }
    }
    Hint("또래 참고 범위를 맞출 때만 씁니다. 화면에 표시되지 않아요.")

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            Field("키 (cm)", if (p.heightCm > 0) p.heightCm.toInt().toString() else "", KeyboardType.Number, "168") {
                set(p.copy(heightCm = it.filter(Char::isDigit).take(3).toDoubleOrNull() ?: 0.0))
            }
        }
        Box(Modifier.weight(1f)) {
            Field("몸무게 (kg)", if (p.weightKg > 0) p.weightKg.toInt().toString() else "", KeyboardType.Number, "62") {
                set(p.copy(weightKg = it.filter(Char::isDigit).take(3).toDoubleOrNull() ?: 0.0))
            }
        }
    }
    Hint("여기 적은 값은 계정에만 저장되고 외부 제공에 쓰지 않습니다.")

    SectionRow("임신 중이신가요", "안전 확인")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PickChip("아니요", p.pregnancyStatus == PregnancyStatus.NOT_PREGNANT) {
            set(p.copy(pregnancyStatus = PregnancyStatus.NOT_PREGNANT))
        }
        PickChip("네", p.pregnancyStatus == PregnancyStatus.PREGNANT) {
            set(p.copy(pregnancyStatus = PregnancyStatus.PREGNANT))
        }
        PickChip("답하지 않음", p.pregnancyStatus == PregnancyStatus.UNKNOWN) {
            set(p.copy(pregnancyStatus = PregnancyStatus.UNKNOWN))
        }
    }
    Hint("건강 참고 정보를 보여드려도 되는지 판단하는 데만 씁니다. 점수 계산에는 넣지 않아요.")
}

/* ---------------------------------------------------- 2단계 · 운동 정보 */

@Composable
private fun ExerciseStep(p: UserProfile, set: (UserProfile) -> Unit) {
    Text("운동은 어떻게\n하고 계세요?", style = TmtnText.Headline, color = TmtnColor.OnSurface)
    Text("대략이면 충분해요. 틈튼지수 계산에 씁니다.", style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant)

    /* ── 근력운동 ─────────────────────────────────── */
    SectionRow("근력운동", "주 횟수")
    Hint("팔굽혀펴기 · 스쿼트 · 기구 운동 등")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0 to "안 함", 1 to "주 1회", 2 to "주 2회", 3 to "주 3회", 4 to "주 4회", 5 to "주 5회+")
            .forEach { (n, label) ->
                PickChip(label, p.strengthDaysWeek == n) { set(p.copy(strengthDaysWeek = n)) }
            }
    }

    SectionRow("보통 어느 정도 힘들게 하세요?", "강도")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StrengthIntensity.entries.forEach { s ->
            Box(Modifier.weight(1f)) {
                IntensityCard(s.label, s.hint, p.strengthIntensity == s) { set(p.copy(strengthIntensity = s)) }
            }
        }
    }

    /* ── 유산소운동 ───────────────────────────────── */
    SectionRow("유산소 운동", "강도별 주당 시간")

    AerobicStepper(AerobicBand.LIGHT, p.aerobicLightMinWeek) { set(p.copy(aerobicLightMinWeek = it)) }
    AerobicStepper(AerobicBand.MODERATE, p.aerobicModerateMinWeek) { set(p.copy(aerobicModerateMinWeek = it)) }
    AerobicStepper(AerobicBand.VIGOROUS, p.aerobicVigorousMinWeek) { set(p.copy(aerobicVigorousMinWeek = it)) }

    Hint("0분도 괜찮아요. 하지 않는다면 0으로 두세요.")
    NoteBox(
        body = "세 가지 강도를 모두 채워 주세요. 나중에 내 정보에서 고칠 수 있어요. " +
            "저강도는 참고용이고, 참고 정보 계산에는 중강도와 고강도 시간만 들어갑니다.",
    )
}

/* ---------------------------------------------------------- 조각들 */

@Composable
private fun StepBar(step: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) { i ->
            Box(
                Modifier.weight(1f).height(6.dp).clip(TmtnShape.Chip)
                    .background(if (i < step) TmtnColor.Primary else TmtnColor.OutlineVariant),
            )
        }
    }
}

@Composable
private fun SectionRow(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = TmtnText.Label, color = TmtnColor.OnSurface)
        Spacer(Modifier.weight(1f))
        Text(trailing, style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
}

@Composable
private fun Field(
    label: String,
    value: String,
    type: KeyboardType,
    hint: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(hint, style = TmtnText.Body, color = TmtnColor.OnDisabled) },
        singleLine = true,
        shape = TmtnShape.Input,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = TmtnColor.Surface,
            unfocusedContainerColor = TmtnColor.Surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = TmtnText.Caption) },
        shape = TmtnShape.Chip,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = TmtnColor.Surface,
            labelColor = TmtnColor.OnSurfaceVariant,
            selectedContainerColor = TmtnColor.SecondaryContainer,
            selectedLabelColor = TmtnColor.OnSurface,
        ),
    )
}

/** 근력 강도 카드 — 피그마 A08 의 3개 박스 */
@Composable
private fun IntensityCard(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(TmtnShape.SmallCard)
            .background(if (selected) TmtnColor.SecondaryContainer else TmtnColor.Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = TmtnText.Label, color = TmtnColor.OnSurface)
        Text(hint, style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
    }
}

/** 유산소 강도별 주당 시간 — 피그마 A08 의 − / 숫자 / + 스테퍼 */
@Composable
private fun AerobicStepper(band: AerobicBand, value: Int?, onChange: (Int) -> Unit) {
    val current = value ?: 0
    TmtnCardBox(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(TmtnShape.Chip).background(TmtnColor.SecondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(band.label, style = TmtnText.Caption, color = TmtnColor.OnSurface)
            }
            Spacer(Modifier.width(8.dp))
            Text(band.hint, style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−") { onChange((current - 10).coerceAtLeast(0)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$current", style = TmtnText.Display, color = TmtnColor.OnSurface)
                    Spacer(Modifier.width(4.dp))
                    Text("분", style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant)
                }
                Text(
                    "일주일 기준", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            StepButton("+") { onChange((current + 10).coerceAtMost(1440)) }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(TmtnShape.SmallCard)
            .background(TmtnColor.SecondaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TmtnText.Title, color = TmtnColor.OnSurface)
    }
}
