package com.tmtn.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.onboarding.TmtnTextField
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Figma F15 · 이메일 변경 (2단계: 새 이메일 입력 -> 인증번호 확인, 화면 하나로 통합) */
@Composable
fun EmailChangeScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val currentEmail = state.userInfo.value?.email ?: ""
    var newEmail by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val codeSent = state.emailChangeCodeSent.value

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "이메일 변경", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("지금 주소", style = TmtnType.caption, color = colors.onSurfaceVariant)
                Text(currentEmail, style = TmtnType.body, color = colors.onSurface)
            }

            if (!codeSent) {
                TmtnTextField(
                    value = newEmail, onValueChange = { newEmail = it }, label = "새 이메일",
                    supportingText = "인증번호를 받을 수 있는 주소를 입력해 주세요.", keyboardType = KeyboardType.Email,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                ) {
                    Text("새 주소로 인증번호를 보냅니다. 인증을 마쳐야 바뀝니다.", style = TmtnType.body, color = colors.onSurface)
                }
                Text("바뀌기 전까지는 지금 주소로 로그인합니다.", style = TmtnType.body, color = colors.onSurfaceVariant)
                TmtnPrimaryButton(
                    text = "인증번호 받기",
                    onClick = { scope.launch { state.requestEmailChangeCode(newEmail) } },
                    enabled = newEmail.contains("@"),
                )
            } else {
                Text(
                    "${state.pendingNewEmail.value}로 보낸 인증번호를 입력해 주세요.",
                    style = TmtnType.body, color = colors.onSurfaceVariant,
                )
                TmtnTextField(
                    value = code, onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
                    label = "인증번호", keyboardType = KeyboardType.Number,
                )
                TmtnPrimaryButton(
                    text = "확인하고 변경",
                    onClick = { scope.launch { state.confirmEmailChange(code) } },
                    enabled = code.length == 6,
                )
                TmtnTextButton(text = "다른 이메일로 다시 받기", onClick = { state.emailChangeCodeSent.value = false })
            }
        }
    }
}

/** Figma F16 · 비밀번호 변경 */
@Composable
fun PasswordChangeScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "비밀번호 변경", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TmtnTextField(value = currentPassword, onValueChange = { currentPassword = it }, label = "지금 비밀번호", isPassword = true)
            TmtnTextField(value = newPassword, onValueChange = { newPassword = it }, label = "새 비밀번호", isPassword = true)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("· 8자 이상", style = TmtnType.label, color = colors.onSurfaceVariant)
                Text("· 문자와 숫자를 함께 넣어 주세요", style = TmtnType.caption, color = colors.onSurfaceVariant)
                Text("· 이전 비밀번호와 다르게 정해 주세요", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }

            TmtnTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = "새 비밀번호 확인", isPassword = true)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "비밀번호를 바꾸면 다른 기기의 로그인이 모두 해제됩니다. 이 기기에서는 그대로 이어집니다.",
                    style = TmtnType.body, color = colors.onSurface,
                )
            }

            TmtnPrimaryButton(
                text = "비밀번호 바꾸기",
                onClick = { scope.launch { state.changePassword(currentPassword, newPassword) } },
                enabled = currentPassword.isNotBlank() && newPassword.length >= 8 && newPassword == confirmPassword,
            )
        }
    }
}

/** Figma F17 · 계정 삭제 재인증 */
@Composable
fun AccountDeleteReauthScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    var password by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "계정 삭제", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(16.dp)).padding(20.dp),
            ) {
                Text("이 단계를 지나면 되돌릴 수 없습니다", style = TmtnType.bodyLarge, color = colors.error)
            }
            Text("본인 확인이 필요합니다", style = TmtnType.headline, color = colors.onSurface)
            Text("계정을 지우기 전에 비밀번호를 한 번 더 확인합니다.", style = TmtnType.body, color = colors.onSurfaceVariant)

            TmtnTextField(value = password, onValueChange = { password = it }, label = "비밀번호", isPassword = true)

            Row(
                modifier = Modifier.fillMaxWidth().clickable { confirmed = !confirmed },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                Text(
                    "기록·재료·댐이 모두 사라지는 것을 확인했습니다.",
                    style = TmtnType.body, color = colors.onSurface, modifier = Modifier.weight(1f),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        if (confirmed && password.isNotBlank()) colors.error else colors.disabledContainer,
                        RoundedCornerShape(14.dp),
                    )
                    .clickable(enabled = confirmed && password.isNotBlank()) {
                        scope.launch { state.deleteAccount(password) }
                    },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "확인하고 계속", style = TmtnType.label,
                    color = if (confirmed && password.isNotBlank()) Color.White else colors.onDisabled,
                )
            }
            TmtnTextButton(text = "그만두기", onClick = onBack)
        }
    }
}

/** Figma F18 · 삭제 완료 */
@Composable
fun AccountDeletedScreen(onGoHome: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        com.tmtn.app.ui.common.TmtnMascot(com.tmtn.app.R.drawable.beaver_wave,
            "손을 흔드는 비버", Modifier.fillMaxWidth().height(220.dp))
        Text("계정을 지웠어요", style = TmtnType.headline, color = colors.onSurface)
        Text("그동안 함께해 주셔서 고맙습니다.", style = TmtnType.body, color = colors.onSurfaceVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(16.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text("기록 · 재료 · 댐은 모두 지웠습니다. 삭제 상태 조회나 복구는 제공하지 않습니다.", style = TmtnType.body, color = colors.onSurface)
        }
        Text(
            "법령이 정한 최소 보관 기간이 있는 항목은 그 기간이 끝난 뒤 지웁니다.",
            style = TmtnType.body, color = colors.onSurfaceVariant,
        )
        TmtnPrimaryButton(text = "홈으로 가기", onClick = onGoHome)
    }
}

/** Figma F22 · 로그아웃 확인 다이얼로그 */
@Composable
fun LogoutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalTmtnColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("로그아웃할까요?", style = TmtnType.title, color = colors.onSurface) },
        text = {
            Text(
                "기록은 그대로 남습니다. 같은 계정으로 다시 로그인하면 이어서 볼 수 있습니다.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
            )
        },
        confirmButton = {
            Text(
                "로그아웃", style = TmtnType.label, color = colors.error,
                modifier = Modifier.clickable { onConfirm() }.padding(8.dp),
            )
        },
        dismissButton = {
            Text(
                "그만두기", style = TmtnType.label, color = colors.primary,
                modifier = Modifier.clickable { onDismiss() }.padding(8.dp),
            )
        },
    )
}
