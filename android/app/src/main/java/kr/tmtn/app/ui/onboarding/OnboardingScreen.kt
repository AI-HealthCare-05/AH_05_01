package kr.tmtn.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kr.tmtn.app.designsystem.*
import kr.tmtn.app.domain.ml.Sex
import kr.tmtn.app.domain.model.UserProfile

/**
 * 온보딩 (A 온보딩). 필수는 네 가지뿐이다 — 출생연도 · 키 · 몸무게 · 주간 활동.
 *
 * 받지 않는 것: 주민등록번호, 전체 생년월일, 혈압·혈당 수치.
 * 성별은 조사 자료와 비교하기 위한 선택 항목이고, 건너뛸 수 있다.
 */
@Composable
fun OnboardingScreen(initial: UserProfile, onDone: (UserProfile) -> Unit) {
    var nickname by remember { mutableStateOf(initial.nickname) }
    var birthYear by remember { mutableStateOf(if (initial.birthYear > 0) initial.birthYear.toString() else "") }
    var sex by remember { mutableStateOf(initial.sex) }
    var height by remember { mutableStateOf(if (initial.heightCm > 0) initial.heightCm.toInt().toString() else "") }
    var weight by remember { mutableStateOf(if (initial.weightKg > 0) initial.weightKg.toInt().toString() else "") }
    var waist by remember { mutableStateOf(initial.waistCm?.toInt()?.toString() ?: "") }
    var aerobic by remember { mutableStateOf(initial.aerobicMinutesPerWeek.toString()) }
    var strength by remember { mutableStateOf(initial.strengthDaysPerWeek.toString()) }
    var slot by remember { mutableStateOf(initial.preferredSlot.ifEmpty { "언제나" }) }

    val year = birthYear.toIntOrNull() ?: 0
    val h = height.toDoubleOrNull() ?: 0.0
    val w = weight.toDoubleOrNull() ?: 0.0
    val ready = year in 1900..2020 && h > 80 && w > 20

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("먼저 몇 가지만 알려 줘", style = TmtnText.Headline, color = TmtnColor.OnSurface)
        Text(
            "오늘의 카드를 맞춰 주는 데 쓰는 값이에요. 나중에 마이에서 바꿀 수 있어요.",
            style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant,
        )

        Field("어떻게 부를까요", nickname, { nickname = it }, KeyboardType.Text, "홍주")
        Field("출생연도", birthYear, { birthYear = it.filter(Char::isDigit).take(4) }, KeyboardType.Number, "1996")

        Text("성별 (선택)", style = TmtnText.Label, color = TmtnColor.OnSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SexChip("여성", sex == Sex.FEMALE) { sex = if (sex == Sex.FEMALE) null else Sex.FEMALE }
            SexChip("남성", sex == Sex.MALE) { sex = if (sex == Sex.MALE) null else Sex.MALE }
            SexChip("건너뛰기", sex == null) { sex = null }
        }
        Text(
            "조사 자료와 비교할 때만 써요. 넣지 않아도 앱은 그대로 동작해요.",
            style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { Field("키 (cm)", height, { height = it.filter(Char::isDigit).take(3) }, KeyboardType.Number, "168") }
            Box(Modifier.weight(1f)) { Field("몸무게 (kg)", weight, { weight = it.filter(Char::isDigit).take(3) }, KeyboardType.Number, "62") }
        }

        Field("허리둘레 (cm, 선택)", waist, { waist = it.filter(Char::isDigit).take(3) }, KeyboardType.Number, "재본 적 없으면 비워 두세요")
        NoteBox(
            tone = NoteTone.Neutral,
            body = "허리둘레를 비워 두면 키·몸무게로 추정한 값을 대신 씁니다. 직접 잰 값이 있으면 그 값이 항상 우선이에요.",
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { Field("주간 유산소 (분)", aerobic, { aerobic = it.filter(Char::isDigit).take(3) }, KeyboardType.Number, "150") }
            Box(Modifier.weight(1f)) { Field("주간 근력 (일)", strength, { strength = it.filter(Char::isDigit).take(1) }, KeyboardType.Number, "2") }
        }

        Text("주로 움직이기 좋은 때", style = TmtnText.Label, color = TmtnColor.OnSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("아침", "오후", "저녁", "언제나").forEach {
                SexChip(it, slot == it) { slot = it }
            }
        }

        Spacer(Modifier.height(8.dp))
        TmtnFilledButton(
            text = "다 됐어요",
            enabled = ready,
            disabledReason = "출생연도·키·몸무게까지 넣으면 다음으로 갈 수 있어요.",
            onClick = {
                onDone(
                    UserProfile(
                        nickname = nickname.ifBlank { "친구" },
                        birthYear = year,
                        sex = sex,
                        heightCm = h,
                        weightKg = w,
                        waistCm = waist.toDoubleOrNull(),
                        aerobicMinutesPerWeek = aerobic.toIntOrNull() ?: 0,
                        strengthDaysPerWeek = strength.toIntOrNull() ?: 0,
                        preferredSlot = slot,
                    ),
                )
            },
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, type: KeyboardType, hint: String) {
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
private fun SexChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = TmtnText.Label) },
        shape = TmtnShape.Chip,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = TmtnColor.Surface,
            labelColor = TmtnColor.OnSurfaceVariant,
            selectedContainerColor = TmtnColor.SecondaryContainer,
            selectedLabelColor = TmtnColor.OnSurface,
        ),
    )
}
