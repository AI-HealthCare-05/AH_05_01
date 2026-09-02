package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.background
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
    val colors = LocalTmtnColors.current

    LaunchedEffect(Unit) {
        delay(1200)
        state.step.value = OnboardingStep.A02_START
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // 로고 자리 (Figma 원본도 미확정 이미지 슬롯)
            Box(
                modifier = Modifier
                    .size(152.dp)
                    .background(colors.secondaryContainer, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.secondary, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("로고\n(미확정)", style = TmtnType.caption, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            }

            Text("틈튼", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text("하루 한 장, 오늘의 작은 행동", style = TmtnType.bodyLarge, color = colors.onSurfaceVariant)

            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(4.dp)
                    .background(colors.outlineVariant, RoundedCornerShape(2.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(4.dp)
                        .background(colors.primary, RoundedCornerShape(2.dp)),
                )
            }
            Text("오늘 상태를 불러오는 중이에요", style = TmtnType.caption, color = colors.onSurfaceVariant)
        }

        Text(
            "교육·연구 목적 프로토타입입니다. 의료기기가 아닙니다.",
            style = TmtnType.caption, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).fillMaxWidth(),
        )
    }
}

/** Figma A02 · 시작 (로그인·가입) (node 99:21) */
@Composable
fun A02StartScreen(state: OnboardingState) {
    val colors = LocalTmtnColors.current

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(colors.secondaryContainer, RoundedCornerShape(16.dp))
                .border(1.dp, colors.secondary, RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "마스코트 · 전신 비버\n(이미지 미확정)",
                style = TmtnType.caption, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("하루 한 장이면 충분해", style = TmtnType.headline, color = colors.onSurface, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "카드 한 장을 고르고, 오늘 할 수 있는 작은 행동 하나만 하면 돼. 나머지는 내가 기록해 둘게.",
            style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(48.dp))
        TmtnPrimaryButton(text = "이메일로 시작하기", onClick = { state.step.value = OnboardingStep.A03_SIGNUP })
        Spacer(modifier = Modifier.height(8.dp))
        TmtnOutlinedButton(text = "이미 계정이 있어요", onClick = { state.step.value = OnboardingStep.A05_LOGIN })

        Spacer(modifier = Modifier.height(16.dp))
        Text("TMTN — Tiny Moves, Tomorrow's Normal.", style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}

/** Figma A05 · 로그인 (node 99:129, 재방문자용) */
@Composable
fun A05LoginScreen(state: OnboardingState, scope: CoroutineScope, onLoginSuccess: () -> Unit) {
    val colors = LocalTmtnColors.current
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
                supportingText = "8자 이상, 대/소문자·숫자·특수문자를 각각 포함해 주세요.", isPassword = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onImeAction = { keyboardController?.hide() },
                modifier = Modifier.focusRequester(passwordFocus),
            )

            TmtnPrimaryButton(
                text = "로그인",
                onClick = { scope.launch { state.login(onLoginSuccess) } },
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
    var email by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }

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
            Text("가입한 메일 주소를\n알려 주세요", style = TmtnType.headline, color = colors.onSurfaceVariant)
            Text(
                "그 주소로 비밀번호를 다시 정할 수 있는 링크를 보내드립니다.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )
            TmtnTextField(
                value = email, onValueChange = { email = it }, label = "이메일",
                supportingText = "가입할 때 사용한 주소를 입력해 주세요.", keyboardType = KeyboardType.Email,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "계정이 있는지는 알려드리지 않습니다. 등록되지 않은 주소를 넣어도 같은 안내가 표시됩니다.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
                Text("링크는 30분 동안만 쓸 수 있습니다.", style = TmtnType.body, color = colors.onSurfaceVariant)
            }

            notice?.let {
                Text("⚠️ $it", style = TmtnType.caption, color = colors.error)
            }

            TmtnPrimaryButton(
                text = "재설정 링크 받기",
                onClick = { notice = "이 기능은 아직 준비 중이에요. 백엔드 연동 후에 이용할 수 있어요." },
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
                supportingText = "8자 이상, 대/소문자·숫자·특수문자를 각각 포함해 주세요.", isPassword = true,
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
                onClick = { notice = "이 기능은 아직 준비 중이에요. 백엔드 연동 후에 이용할 수 있어요." },
            )
        }
    }
}
