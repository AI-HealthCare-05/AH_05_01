package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/** Figma A07 · 필수 입력 · 기본 정보 (node 136:148), 1/2단계 */
@Composable
fun A07ProfileScreen(state: OnboardingState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    var name by state.name
    var nickname by state.nickname
    var gender by state.gender
    var birthYear by state.birthYear
    var birthMonth by state.birthMonth
    var isPregnant by state.isPregnant
    var heightCm by state.heightCm
    var weightKg by state.weightKg

    // 이름 -> 닉네임 -> 키 -> 몸무게 순서로 키보드 "다음" 눌렀을 때 자동 이동
    val nicknameFocus = remember { FocusRequester() }
    val heightFocus = remember { FocusRequester() }
    val weightFocus = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "필수 입력", onBack = { state.step.value = OnboardingStep.A06_CONSENT })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                TmtnEqualProgressBar(totalSteps = 2, currentStep = 1)
                Spacer(modifier = Modifier.height(8.dp))
                Text("1 / 2단계 · 모두 필요한 값이에요", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            Text("시작하기 전에\n몇 가지만 알려 주세요", style = TmtnType.headline, color = colors.onSurface)

            // 이름 · 닉네임
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TmtnTextField(
                    value = name, onValueChange = { name = it }, label = "이름",
                    imeAction = ImeAction.Next,
                    onImeAction = { nicknameFocus.requestFocus() },
                    modifier = Modifier.weight(1f),
                )
                TmtnTextField(
                    value = nickname, onValueChange = { nickname = it }, label = "닉네임 (선택)",
                    imeAction = ImeAction.Done,
                    onImeAction = { keyboardController?.hide() },
                    modifier = Modifier.weight(1f).focusRequester(nicknameFocus),
                )
            }
            Text("닉네임을 비우면 이름을 그대로 씁니다.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            // 생년월일 - 드롭다운(항목 90개) 대신 컴팩트 스테퍼로 변경 (너무 길다는 피드백 반영)
            SectionHeader(title = "생년월일", hint = "연·월만")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TmtnCompactStepper(
                    value = birthYear, unit = "년",
                    onDecrement = { if (birthYear > 1930) birthYear-- },
                    onIncrement = { if (birthYear < 2020) birthYear++ },
                    modifier = Modifier.weight(1f),
                )
                TmtnCompactStepper(
                    value = birthMonth, unit = "월",
                    onDecrement = { birthMonth = if (birthMonth > 1) birthMonth - 1 else 12 },
                    onIncrement = { birthMonth = if (birthMonth < 12) birthMonth + 1 else 1 },
                    modifier = Modifier.weight(1f),
                )
            }
            Text("정확한 날짜는 받지 않습니다. 연·월만 있으면 충분해요.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            // 성별
            SectionHeader(title = "성별", hint = "또래 참고 범위용")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TmtnChip(text = "남성", selected = gender == "MALE", onClick = { gender = "MALE" })
                TmtnChip(text = "여성", selected = gender == "FEMALE", onClick = { gender = "FEMALE" })
            }
            Text("또래 참고 범위를 맞출 때만 씁니다. 화면에 표시되지 않아요.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            // ⚠️ 2026-09-04 추가: 틈튼지수 실모델 입력 계약("임신 여부를 명시적으로 알아야
            // 계산 가능")을 위해 추가. 여성일 때만 물음 - 남성은 생물학적으로 해당 사항이
            // 없어서 서버가 자동으로 "해당 없음"으로 처리함(_get_pregnancy_status() 참고).
            if (gender == "FEMALE") {
                SectionHeader(title = "임신 여부", hint = "건강 참고 점수 계산용")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TmtnChip(text = "아니요", selected = isPregnant == false, onClick = { isPregnant = false })
                    TmtnChip(text = "예", selected = isPregnant == true, onClick = { isPregnant = true })
                }
                Text(
                    "임신 중에는 참고 점수를 정확히 계산하기 어려워 일부 항목을 표시하지 않을 수 있어요.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            }

            // 키 · 몸무게 - Next/Done 키보드 액션으로 자동 이동 + 완료 시 키보드 자동으로 닫힘
            // 숫자만, 3자리(최대 999)까지만 입력 가능 (그 이상은 비현실적인 값이라 막음)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TmtnTextField(
                    value = heightCm,
                    onValueChange = { input -> heightCm = input.filter(Char::isDigit).take(3) },
                    label = "키 (cm)",
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Next,
                    onImeAction = { weightFocus.requestFocus() },
                    modifier = Modifier.weight(1f).focusRequester(heightFocus),
                )
                TmtnTextField(
                    value = weightKg,
                    onValueChange = { input -> weightKg = input.filter(Char::isDigit).take(3) },
                    label = "몸무게 (kg)",
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f).focusRequester(weightFocus),
                )
            }
            Text("여기 적은 값은 계정에만 저장되고 외부 제공에 쓰지 않습니다.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            Spacer(modifier = Modifier.height(8.dp))
            // ⚠️ 2026-09-06 QA(P0-2) 반영: enabled 조건이 아예 없어서 키·몸무게를 비운
            // 채로도 "다음"이 그대로 넘어갔음(허리둘레·틈튼지수 모델 입력이라 뒤에서
            // "계산 안 됨"으로 이어짐 - P1-11). 최소한 값이 채워졌을 때만 진행되게 막음.
            TmtnPrimaryButton(
                text = "다음",
                onClick = { scope.launch { state.submitProfile() } },
                enabled = heightCm.isNotBlank() && weightKg.isNotBlank(),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String) {
    val colors = LocalTmtnColors.current
    Row(
        modifier = Modifier.fillMaxWidth().height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).background(colors.secondary, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = TmtnType.label, color = colors.onSurface)
        Spacer(modifier = Modifier.weight(1f))
        Text(hint, style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}
