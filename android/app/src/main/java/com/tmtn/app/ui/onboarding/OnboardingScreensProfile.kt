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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.tmtn.app.ui.common.ResponsiveFieldPair
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
    var yearText by rememberSaveable(state.birthDateConfirmed.value) { mutableStateOf(if (state.birthDateConfirmed.value) birthYear.toString() else "") }
    var monthText by rememberSaveable(state.birthDateConfirmed.value) { mutableStateOf(if (state.birthDateConfirmed.value) birthMonth.toString() else "") }
    val birthDateValid = isValidBirthMonth(yearText, monthText)

    // 이름 -> 닉네임 -> 키 -> 몸무게 순서로 키보드 "다음" 눌렀을 때 자동 이동
    val nicknameFocus = remember { FocusRequester() }
    val heightFocus = remember { FocusRequester() }
    val weightFocus = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "나를 소개하기", onBack = { state.step.value = OnboardingStep.SIGNUP_COMPLETE })

        Column(
            modifier = Modifier
                .fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {


            Text("너의 하루를 알려 줘.", style = TmtnType.headline, color = colors.onSurface)
            Text("1 / 2 · 내 정보   다음은 평소 운동이에요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
            Text("이 정보로 무리 없는 미션을 준비할게. 별명으로 편하게 불러 줄게!", style = TmtnType.body, color = colors.onSurface)
            // 이름 · 별명
            ResponsiveFieldPair(first = { fieldModifier ->
                TmtnTextField(
                    // ⚠️ 2026-09-08 QA 반영: 닉네임에만 "(선택)"이 붙어 있어서 이름도 선택인
                    // 것처럼 보였음(실제로 안 써도 넘어갔음) - 필수임을 라벨에 명시.
                    value = name, onValueChange = { name = it.take(20) }, label = "이름 · 필수",
                    imeAction = ImeAction.Next,
                    onImeAction = { nicknameFocus.requestFocus() },
                    modifier = fieldModifier,
                )
            }, second = { fieldModifier ->
                TmtnTextField(
                    value = nickname, onValueChange = { nickname = it.take(20) }, label = "별명 · 선택",
                    imeAction = ImeAction.Done,
                    onImeAction = { keyboardController?.hide() },
                    modifier = fieldModifier.focusRequester(nicknameFocus),
                )
            })
            Text("별명은 앱 화면에 표시되는 이름이에요. 비워 두면 이름을 표시해요.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            SectionHeader(title = "생년월 · 필수", hint = "태어난 연도와 월만")
            ResponsiveFieldPair(first = { fieldModifier ->
                TmtnTextField(yearText, { yearText = it.filter(Char::isDigit).take(4) }, "태어난 연도", fieldModifier,
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            }, second = { fieldModifier ->
                TmtnTextField(monthText, { monthText = it.filter(Char::isDigit).take(2) }, "월", fieldModifier,
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
            })

            // 성별
            SectionHeader(title = "성별 · 필수", hint = "")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TmtnChip(text = "남성", selected = gender == "MALE", onClick = { gender = "MALE" })
                TmtnChip(text = "여성", selected = gender == "FEMALE", onClick = { gender = "FEMALE" })
            }


            // ⚠️ 2026-09-04 추가: 틈튼지수 실모델 입력 계약("임신 여부를 명시적으로 알아야
            // 계산 가능")을 위해 추가. 여성일 때만 물음 - 남성은 생물학적으로 해당 사항이
            // 없어서 서버가 자동으로 "해당 없음"으로 처리함(_get_pregnancy_status() 참고).
            if (gender == "FEMALE") {
                SectionHeader(title = "임신 여부 (필수)", hint = "건강 참고 점수 계산용")
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
            ResponsiveFieldPair(first = { fieldModifier ->
                TmtnTextField(
                    value = heightCm,
                    onValueChange = { input -> heightCm = input.filter(Char::isDigit).take(3) },
                    label = "키 · 필수 (cm)",
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Next,
                    onImeAction = { weightFocus.requestFocus() },
                    modifier = fieldModifier.focusRequester(heightFocus),
                )
            }, second = { fieldModifier ->
                TmtnTextField(
                    value = weightKg,
                    onValueChange = { input -> weightKg = input.filter(Char::isDigit).take(3) },
                    label = "몸무게 · 필수 (kg)",
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
                    modifier = fieldModifier.focusRequester(weightFocus),
                )
            })
            Text("맞춤 미션과 동의한 분석에 사용해요. 입력한 정보는 내 정보에서 고칠 수 있어요.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            Spacer(modifier = Modifier.height(8.dp))
            // ⚠️ 2026-09-06 QA(P0-2) 반영: enabled 조건이 아예 없어서 키·몸무게를 비운
            // 채로도 "다음"이 그대로 넘어갔음(허리둘레·틈튼지수 모델 입력이라 뒤에서
            // "계산 안 됨"으로 이어짐 - P1-11). 최소한 값이 채워졌을 때만 진행되게 막음.
            // ⚠️ 2026-09-08 QA 반영: 이 화면 제목이 "필수 입력"인데 실제로 막고 있던 건
            // 키·몸무게·성별뿐이었음. 이름을 안 써도, 여성인데 임신 여부를 안 골라도 그냥
            // 넘어갔음(임신 여부는 틈튼지수 실모델의 필수 입력이라, 비면 건강 영역이 통째로
            // 미산출됨 - _get_pregnancy_status()가 "모른다"를 임신 아님으로 넘겨짚지 않음).
            // 닉네임은 원래 선택 항목이라 그대로 둠.
            val needsPregnancyAnswer = gender == "FEMALE" && isPregnant == null
            val missingReason = when {
                name.isBlank() -> "이름을 입력해 주세요."
                !birthDateValid -> "태어난 연도 네 자리와 월을 확인해 주세요. 만 14세부터 이용할 수 있어요."
                gender == null -> "성별을 선택해 주세요."
                needsPregnancyAnswer -> "임신 여부를 선택해 주세요."
                (heightCm.toIntOrNull() ?: 0) <= 0 -> "키를 입력해 주세요."
                (weightKg.toIntOrNull() ?: 0) <= 0 -> "몸무게를 입력해 주세요."
                else -> null
            }
            TmtnPrimaryButton(
                text = if (state.isLoading.value) "정보 저장 중…" else "평소 운동 알려주기",
                onClick = {
                    birthYear = yearText.toInt(); birthMonth = monthText.toInt(); state.birthDateConfirmed.value = true
                    scope.launch { state.submitProfile() }
                },
                enabled = missingReason == null && !state.isLoading.value,
                disabledReason = missingReason,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String) {
    val colors = LocalTmtnColors.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = TmtnType.label, color = colors.onSurface, modifier = Modifier.weight(1f))
        Text(hint, style = TmtnType.caption, color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
