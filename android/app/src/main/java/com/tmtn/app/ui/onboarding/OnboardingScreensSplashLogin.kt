package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/** Figma A01 · 스플래시 (node 99:2). 잠깐 보여주고 자동으로 A02로 넘어감. */
@Composable
fun A01SplashScreen(state: OnboardingState) {
    com.tmtn.app.ui.launch.TmtnLaunchOverlay(
        ready = true,
        onFinished = { state.step.value = OnboardingStep.A02_START },
    )
}

/** Figma A02 · 시작 (로그인·가입) (node 99:21) */
@Composable
fun A02StartScreen(state: OnboardingState, scope: CoroutineScope, onLoginSuccess: () -> Unit) {
    val colors = LocalTmtnColors.current
    // ⚠️ 2026-09-10: Credential Manager의 계정 선택 시트는 화면 위에 떠야 해서 Activity
    // 컨텍스트가 필요함. setContent가 MainActivity 안에 있으므로 여기 LocalContext가 그 Activity임.
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        com.tmtn.app.ui.common.TmtnMascot(com.tmtn.app.R.drawable.beaver_standing, "인사하는 비버",
            Modifier.fillMaxWidth().height(240.dp))
        Text("하루 한 장의 실천", style = TmtnType.headline, color = colors.onSurface, textAlign = TextAlign.Center)
        Text("작은 실천이 쌓여 비버의 댐이 자라요.",
            style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        TmtnPrimaryButton("이메일로 시작하기", onClick = { state.step.value = OnboardingStep.A03_SIGNUP })
        // ⚠️ 2026-09-10 추가: 구글 계정 연동 로그인.
        // 가입·로그인 겸용 버튼임 - 처음 오는 사람에겐 가입, 이미 있는 사람에겐 로그인으로
        // 서버가 알아서 갈라줌. 처음이면 계정을 바로 만들지 않고 약관 동의(A06)부터 태움.
        TmtnGoogleButton(
            onClick = { scope.launch { state.loginWithGoogle(context, onLoginSuccess) } },
            enabled = !state.isLoading.value,
        )
        TmtnTextButton("이미 계정이 있어요", onClick = { state.step.value = OnboardingStep.A05_LOGIN })
    }
}

/** Figma A05 · 로그인 (node 99:129, 재방문자용) */
@Composable
fun A05LoginScreen(state: OnboardingState, scope: CoroutineScope, onLoginSuccess: () -> Unit) {
    val colors = LocalTmtnColors.current
    val context = LocalContext.current
    var email by state.email
    var password by state.password
    val passwordFocus = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "로그인", onBack = { state.step.value = OnboardingStep.A02_START })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("다시 만나서 반가워요.", style = TmtnType.title, color = colors.onSurface)

            TmtnTextField(
                value = email, onValueChange = { email = it }, label = "이메일",
                supportingText = "가입할 때 사용한 주소를 입력해 주세요.", keyboardType = KeyboardType.Email,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                onImeAction = { passwordFocus.requestFocus() },
            )
            TmtnTextField(
                value = password, onValueChange = { password = it }, label = "비밀번호",
                isPassword = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onImeAction = { keyboardController?.hide() },
                modifier = Modifier.focusRequester(passwordFocus),
            )

            TmtnPrimaryButton(
                text = "로그인",
                onClick = { scope.launch { state.login(onLoginSuccess) } },
                // ⚠️ 2026-09-06 QA(P1-8) 반영: 이메일·비밀번호 둘 다 비운 채로도 그대로
                // 서버에 전송돼서 "요청이 실패했어요 (404)" 같은 원인 불명 에러로 이어졌음.
                enabled = email.isNotBlank() && password.isNotBlank(),
            )

            // ⚠️ 2026-09-10 추가: 구글로 가입한 사람이 이 화면에서 비밀번호를 아무리 쳐도
            // 로그인이 안 됨(그 계정엔 비밀번호가 없어서 서버가 409로 안내함). 여기에도
            // 같은 버튼을 둬서 바로 넘어갈 수 있게 함.
            TmtnGoogleButton(
                onClick = { scope.launch { state.loginWithGoogle(context, onLoginSuccess) } },
                enabled = !state.isLoading.value,
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                TmtnTextButton(text = "비밀번호 재설정", onClick = { state.step.value = OnboardingStep.A12_PASSWORD_RESET_REQUEST })
                TmtnTextButton(text = "이메일로 가입하기", onClick = { state.step.value = OnboardingStep.A03_SIGNUP })
            }
        }
    }
}

/**
 * Figma A12 · 비밀번호 재설정 요청 (node 282:659).
 * ⚠️ 백엔드에 비밀번호 재설정 API가 아직 없어요(password_reset_tokens 테이블은 있지만
 * 그걸 쓰는 API가 없음). 화면은 만들어두고, 버튼 누르면 그 사실을 그대로 안내함.
 */
@Composable
fun A12PasswordResetRequestScreen(state: OnboardingState) {
    val colors = LocalTmtnColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        TmtnTopBar(title = "비밀번호 재설정", onBack = { state.step.value = OnboardingStep.A05_LOGIN })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("지금은 비밀번호를\n재설정할 수 없어요", style = TmtnType.headline, color = colors.onSurface)
            Text(
                "재설정 링크를 보낼 수 없어요. 기존 비밀번호로 로그인해 주세요.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )
            TmtnPrimaryButton(
                text = "로그인으로 돌아가기",
                onClick = { state.step.value = OnboardingStep.A05_LOGIN },
            )
        }
    }
}

/**
 * Figma A13 · 새 비밀번호 설정 (node 282:4570).
 * ⚠️ A12와 마찬가지로 백엔드 API가 아직 없어요.
 */
@Composable
fun A13NewPasswordScreen(state: OnboardingState) {
    val colors = LocalTmtnColors.current
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        TmtnTopBar(title = "새 비밀번호", onBack = { state.step.value = OnboardingStep.A05_LOGIN })

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("새 비밀번호를 정해 주세요", style = TmtnType.headline, color = colors.onSurfaceVariant)
            TmtnTextField(
                value = newPassword, onValueChange = { newPassword = it }, label = "새 비밀번호",
                isPassword = true,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("· 8자 이상", style = TmtnType.label, color = colors.onSurfaceVariant)
                Text("· 문자와 숫자를 함께 넣어 주세요", style = TmtnType.caption, color = colors.onSurfaceVariant)
                Text("· 이전 비밀번호와 다르게 정해 주세요", style = TmtnType.body, color = colors.onSurfaceVariant)
            }
            TmtnTextField(
                value = confirmPassword, onValueChange = { confirmPassword = it }, label = "새 비밀번호 확인",
                supportingText = "위와 같은 비밀번호를 한 번 더 입력해 주세요.", isPassword = true,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "비밀번호를 바꾸면 다른 기기의 로그인이 모두 해제됩니다. 이 기기에서는 그대로 이어집니다.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
            }

            notice?.let {
                Text("⚠️ $it", style = TmtnType.caption, color = colors.error)
            }

            TmtnPrimaryButton(
                text = "비밀번호 바꾸기",
                onClick = { notice = "아직 비밀번호를 재설정할 수 없어요." },
            )
        }
    }
}
