package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/** Figma A03 · 이메일 회원가입 (node 99:44) */
@Composable
fun A03SignupScreen(state: OnboardingState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    var email by state.email
    var password by state.password
    var passwordConfirm by state.passwordConfirm
    val passwordFocus = remember { FocusRequester() }
    val passwordConfirmFocus = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "이메일로 시작하기", onBack = { state.step.value = OnboardingStep.A02_START })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("이메일 인증을 마치면 바로 시작할 수 있습니다.", style = TmtnType.body, color = colors.onSurfaceVariant)

            TmtnTextField(
                value = email, onValueChange = { email = it }, label = "이메일",
                supportingText = "인증번호를 받을 주소를 입력해 주세요.", keyboardType = KeyboardType.Email,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                onImeAction = { passwordFocus.requestFocus() },
            )
            TmtnTextField(
                value = password, onValueChange = { password = it }, label = "비밀번호",
                supportingText = "8자 이상, 대/소문자·숫자·특수문자를 각각 포함해 주세요.", isPassword = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                onImeAction = { passwordConfirmFocus.requestFocus() },
                modifier = Modifier.focusRequester(passwordFocus),
            )
            TmtnTextField(
                value = passwordConfirm, onValueChange = { passwordConfirm = it }, label = "비밀번호 확인",
                supportingText = "위와 같은 비밀번호를 한 번 더 입력해 주세요.", isPassword = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onImeAction = { keyboardController?.hide() },
                modifier = Modifier.focusRequester(passwordConfirmFocus),
            )

            TmtnPrimaryButton(text = "인증번호 받기", onClick = { scope.launch { state.requestVerificationCode() } })

            Column {
                TmtnStepBars(totalSteps = 3, currentStep = 1)
                Spacer(modifier = Modifier.height(8.dp))
                Text("1단계 / 3 · 계정 정보", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Figma A04 · 이메일 인증번호 (node 99:90) */
@Composable
fun A04VerifyScreen(state: OnboardingState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    val digits by state.codeDigits

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "이메일 인증", onBack = { state.step.value = OnboardingStep.A03_SIGNUP })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("메일로 보낸\n6자리 숫자를 입력해 주세요.", style = TmtnType.title, color = colors.onSurface)

            // ⚠️ 개발용 — 실제 이메일 발송 전까지만 보이는 임시 배너. 운영 환경에서는
            // devOnlyCode가 항상 null이라 이 블록 자체가 안 그려짐.
            state.devOnlyCode.value?.let { code ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = colors.rewardContainer, shape = RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        "[테스트용] 인증번호: $code\n(실제 이메일 발송 연동 전까지만 여기 표시돼요)",
                        style = TmtnType.caption, color = colors.onSurface,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .background(color = colors.secondaryContainer, shape = RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.email.value, style = TmtnType.caption, color = colors.onSurface)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { state.step.value = OnboardingStep.A03_SIGNUP }) {
                    Text("수정", style = TmtnType.label, color = colors.primary)
                }
            }

            val focusRequesters = remember { List(6) { FocusRequester() } }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                digits.forEachIndexed { index, digit ->
                    TmtnDigitField(
                        value = digit,
                        onValueChange = { newValue ->
                            val updated = digits.toMutableList()
                            updated[index] = newValue
                            state.codeDigits.value = updated
                            // 숫자 하나 입력되면 자동으로 다음 칸으로 포커스 이동
                            if (newValue.length == 1 && index < 5) {
                                focusRequesters[index + 1].requestFocus()
                            }
                        },
                        modifier = Modifier.focusRequester(focusRequesters[index]),
                    )
                }
            }

            TmtnPrimaryButton(
                text = "확인하고 가입 완료",
                onClick = { scope.launch { state.confirmVerificationCode() } },
                enabled = state.verificationCode.length == 6,
            )
            // ⚠️ FLOWS.md 갱신: 예전엔 이 버튼이 바로 재요청했는데, 이제 A11(오류 화면)로
            // 먼저 보내서 "왜 다시 받아야 하는지"를 안내하고 거기서 재요청하게 바뀜.
            TmtnTextButton(text = "인증번호 다시 받기", onClick = { state.step.value = OnboardingStep.A11_VERIFY_RETRY })

            Text(
                "인증번호가 만료되거나 틀리면 계정을 만들지 않고 다시 시도할 수 있는 경로만 보여드립니다. 메일이 오지 않으면 스팸함도 확인해 주세요.",
                style = TmtnType.caption, color = colors.onSurfaceVariant,
            )
        }
    }
}

/** Figma A11 · 인증번호 오류 · 재발송 (node 281:4428) */
@Composable
fun A11VerifyRetryScreen(state: OnboardingState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        TmtnTopBar(title = "이메일 인증", onBack = { state.step.value = OnboardingStep.A04_VERIFY })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("인증번호를\n다시 확인해 주세요", style = TmtnType.headline, color = colors.onSurface)
            Text(
                "입력하신 번호가 맞지 않거나 유효 시간이 지났습니다. 새 번호를 받아 다시 입력해 주세요.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.errorContainer, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "남은 시도 3회 중 ${state.verifyAttemptCount.value}회 사용",
                    style = TmtnType.body, color = colors.error,
                )
                Text(
                    "5회를 넘기면 잠시 후 다시 시도할 수 있습니다.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "인증번호 메일이 스팸함에 들어가 있을 수 있습니다. 메일 주소가 맞는지도 한 번 확인해 주세요.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            }

            TmtnPrimaryButton(text = "인증번호 다시 받기", onClick = { scope.launch { state.resendFromRetryScreen() } })
            TmtnTextButton(text = "메일 주소 수정", onClick = { state.editEmailFromRetryScreen() })
        }
    }
}
