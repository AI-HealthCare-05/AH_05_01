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
import androidx.compose.foundation.layout.heightIn
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

// ⚠️ 2026-09-08 QA 반영: 서버(app/dtos/auth.py, app/core/validators/user_validators.py)와
// 같은 규칙을 앱에서도 먼저 확인하기 위한 헬퍼들. 규칙을 바꿀 때는 양쪽을 같이 고쳐야 함.

/** 서버의 EmailStr과 동등한 수준의 최소 형식 검사(로컬@도메인.최상위, 공백 없음). */
private fun isValidEmail(email: String): Boolean {
    // ⚠️ 2026-09-08 QA 반영: 앞뒤 공백이 조금이라도 있으면 무조건 무효 처리하고 있었음.
    // 모바일 키보드(특히 자동완성 확정 시 스페이스가 따라붙는 경우)에서는 사용자가 보기엔
    // "정확히 입력"했는데도 이 트레일링 스페이스 때문에 계속 형식 오류로 튕겼음 - 이메일
    // 앞뒤 공백은 실수로 붙는 게 대부분이라 그냥 정리하고 검증하는 게 맞음.
    val trimmed = email.trim()
    if (trimmed.length > 40) return false
    return Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$").matches(trimmed)
}

/** 서버 validate_password와 같은 규칙: 8자 이상 + 대문자·소문자·숫자·특수문자 각 1개 이상. */
private fun isValidPassword(password: String): Boolean =
    password.length >= 8 &&
        password.any { it.isUpperCase() } &&
        password.any { it.isLowerCase() } &&
        password.any { it.isDigit() } &&
        password.any { !it.isLetterOrDigit() }

/** 무엇이 빠졌는지 구체적으로 알려줌 - "규칙에 안 맞아요"만 보여주면 뭘 고쳐야 할지 모름. */
private fun passwordRuleHint(password: String): String {
    val missing = buildList {
        if (password.length < 8) add("8자 이상")
        if (password.none { it.isUpperCase() }) add("대문자")
        if (password.none { it.isLowerCase() }) add("소문자")
        if (password.none { it.isDigit() }) add("숫자")
        if (password.none { !it.isLetterOrDigit() }) add("특수문자")
    }
    return if (missing.isEmpty()) "" else "${missing.joinToString("·")}가 필요해요."
}

/** Figma A03 · 이메일 회원가입 (node 99:44) */
@Composable
fun A03SignupScreen(state: OnboardingState, scope: CoroutineScope) {
    val colors = LocalTmtnColors.current
    var email by state.email
    var password by state.password
    var passwordConfirm by state.passwordConfirm
    val isRequesting by state.isLoading
    val passwordFocus = remember { FocusRequester() }
    val passwordConfirmFocus = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "이메일로 시작하기", onBack = { state.step.value = OnboardingStep.A02_START })
        StepProgressHeader(1, 3, "계정 정보")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                // ⚠️ 2026-09-06 QA(레이아웃) 반영: imePadding()만으로는 마지막 필드
                // 아래 "인증번호 받기" 버튼까지는 안 밀려 올라와서 키보드에 덮였음 -
                // 하단에 여유 공간을 더 둬서 스크롤하면 버튼까지 확실히 보이게 함.
                .padding(horizontal = 20.dp, vertical = 16.dp).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("이메일 인증과 약관 동의로 계정을 만들어요.", style = TmtnType.body, color = colors.onSurfaceVariant)

            // ⚠️ 2026-09-08 QA 반영: 이메일 형식·비밀번호 규칙을 앱에서 전혀 안 막고 있었음.
            // 서버(app/dtos/auth.py)는 EmailStr과 validate_password로 이미 거르고 있어서 결국
            // 422로 튕기는데, 그게 이 화면이 아니라 인증번호를 받고 난 다음 단계에서 터져서
            // "왜 안 되는지" 알기 어려웠음. 서버와 같은 규칙을 여기서 미리 확인해서, 틀린
            // 동안에는 아예 진행이 안 되고 무엇이 틀렸는지 그 자리에서 보이게 함.
            val emailTouched = email.isNotBlank()
            val emailValid = isValidEmail(email)
            val passwordTouched = password.isNotBlank()
            val passwordValid = isValidPassword(password)
            val confirmTouched = passwordConfirm.isNotBlank()
            val confirmMatches = password == passwordConfirm

            TmtnTextField(
                value = email, onValueChange = { email = it }, label = "이메일",
                // ⚠️ 2026-09-08 QA 반영: 정확히 입력해도 안내문구가 "입력해 주세요"(안
                // 입력했을 때와 완전히 같은 문구)로 그대로 남아있어서, 마치 입력이 안
                // 된 것처럼 계속 요구받는 느낌이었음. 안 입력/형식 오류/정상 세 단계로
                // 나눠서, 정상이면 확인 문구로 바뀌게 함.
                supportingText = when {
                    !emailTouched -> "인증번호를 받을 주소를 입력해 주세요."
                    !emailValid -> "이메일 형식이 올바르지 않아요. 예: name@example.com"
                    else -> "이 주소로 인증번호를 보내드릴게요."
                },
                keyboardType = KeyboardType.Email,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                onImeAction = { passwordFocus.requestFocus() },
            )
            TmtnTextField(
                value = password, onValueChange = { password = it }, label = "비밀번호",
                supportingText = when {
                    !passwordTouched -> "8자 이상, 대/소문자·숫자·특수문자를 각각 포함해 주세요."
                    !passwordValid -> passwordRuleHint(password)
                    else -> "안전한 비밀번호예요."
                },
                isPassword = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                onImeAction = { passwordConfirmFocus.requestFocus() },
                modifier = Modifier.focusRequester(passwordFocus),
            )
            TmtnTextField(
                value = passwordConfirm, onValueChange = { passwordConfirm = it }, label = "비밀번호 확인",
                supportingText = when {
                    !confirmTouched -> "위와 같은 비밀번호를 한 번 더 입력해 주세요."
                    !confirmMatches -> "위에 입력한 비밀번호와 달라요."
                    else -> "비밀번호가 일치해요."
                },
                isPassword = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onImeAction = { keyboardController?.hide() },
                modifier = Modifier.focusRequester(passwordConfirmFocus),
            )

            // ⚠️ 2026-09-07 QA(N4) 반영: 요청 중 5~6초 동안 버튼이 그대로 눌려있어서 안
            // 눌린 줄 알고 중복 탭 -> 아직 입력도 안 한 인증번호가 "틀렸다"는 엉뚱한 오류로
            // 튀는 문제가 있었음. 요청 중엔 비활성화하고 이유를 보여줘서 중복 탭을 막음.
            val blockedReason = when {
                isRequesting -> "인증번호를 요청하고 있어요…"
                !emailTouched -> "이메일을 입력해 주세요."
                !emailValid -> "이메일 형식을 확인해 주세요."
                !passwordTouched -> "비밀번호를 입력해 주세요."
                !passwordValid -> passwordRuleHint(password)
                !confirmMatches -> "비밀번호 확인이 일치하지 않아요."
                else -> null
            }
            TmtnPrimaryButton(
                text = "인증번호 받기",
                onClick = { scope.launch { state.requestVerificationCode() } },
                enabled = blockedReason == null,
                disabledReason = blockedReason,
            )


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
        StepProgressHeader(2, 3, "이메일 인증")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("인증번호 6자리를 입력해 주세요", style = TmtnType.title, color = colors.onSurface)

            // ⚠️ 개발용 — 실제 이메일 발송 전까지만 보이는 임시 배너. 운영 환경에서는
            // devOnlyCode가 항상 null이라 이 블록 자체가 안 그려짐.
            (if (com.tmtn.app.BuildConfig.DEBUG) state.devOnlyCode.value else null)?.let { code ->
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

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f).heightIn(min = 40.dp)
                        .background(color = colors.surface, shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        modifier = Modifier.weight(1f).focusRequester(focusRequesters[index]),
                    )
                }
            }

            TmtnPrimaryButton(
                text = "약관 확인하기",
                onClick = { state.continueToConsent() },
                enabled = state.verificationCode.length == 6,
            )
            // ⚠️ FLOWS.md 갱신: 예전엔 이 버튼이 바로 재요청했는데, 이제 A11(오류 화면)로
            // 먼저 보내서 "왜 다시 받아야 하는지"를 안내하고 거기서 재요청하게 바뀜.
            TmtnTextButton(text = "인증번호 다시 받기", onClick = { state.step.value = OnboardingStep.A11_VERIFY_RETRY })

            Text(
                "약관에 동의하면 인증번호를 확인하고 가입해요.",
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
            .fillMaxSize(),
    ) {
        TmtnTopBar(title = "이메일 인증", onBack = { state.step.value = OnboardingStep.A04_VERIFY })
        StepProgressHeader(2, 3, "이메일 인증")

        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("인증을 마치지 못했어요", style = TmtnType.headline, color = colors.onSurface)
            Text(
                "인증번호와 연결 상태를 확인해 주세요. 번호가 만료됐다면 새로 받을 수 있어요.",
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
                    "남은 시도 ${(5 - state.verifyAttemptCount.value).coerceAtLeast(0)}회 / 5회",
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
