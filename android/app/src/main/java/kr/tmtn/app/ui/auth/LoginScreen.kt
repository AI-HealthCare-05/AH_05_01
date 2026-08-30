package kr.tmtn.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kr.tmtn.app.designsystem.*

/**
 * 로그인 (A 인증).
 *
 * 지금은 서버가 없어 입력만 확인하고 바로 들어간다.
 * 실제 로그인이 붙을 때 이 화면의 onDone 안쪽만 API 호출로 바꾸면 된다.
 * refresh token 은 이 화면이 아니라 Android Keystore 기반 저장소에 넣어야 한다.
 */
@Composable
fun LoginScreen(onDone: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val canSubmit = email.contains("@") && password.length >= 4

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Text("틈튼", style = TmtnText.Display, color = TmtnColor.Primary)
        Text(
            "틈틈이 탄탄해지는 오늘의 리듬",
            style = TmtnText.BodyLarge,
            color = TmtnColor.OnSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("이메일") },
            singleLine = true,
            shape = TmtnShape.Input,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = TmtnColor.Surface,
                unfocusedContainerColor = TmtnColor.Surface,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("비밀번호") },
            singleLine = true,
            shape = TmtnShape.Input,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = TmtnColor.Surface,
                unfocusedContainerColor = TmtnColor.Surface,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        TmtnFilledButton(
            text = "시작하기",
            onClick = onDone,
            enabled = canSubmit,
            disabledReason = "이메일과 비밀번호(4자 이상)를 입력하면 시작할 수 있어요.",
        )

        TmtnQuietButton("둘러보기", onClick = onDone)

        // [DEMO] 서버가 붙으면 이 NoteBox 를 통째로 지운다.
        // 서버 연동 뒤에는 사실과 달라지는 문장이라 그냥 두면 안 된다.
        NoteBox(
            tone = NoteTone.Neutral,
            title = "아직 데모 단계예요",
            body = "지금은 서버 없이 이 기기 안에서만 동작해요. 입력한 값은 다른 곳으로 보내지 않습니다.",
        )
        Spacer(Modifier.height(40.dp))
    }
}
