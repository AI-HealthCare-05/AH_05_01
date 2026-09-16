package com.tmtn.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.common.ResponsiveFieldPair
import com.tmtn.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Figma F02 1314:6956. Updates the existing users/me endpoint. */
@Composable
internal fun BasicInfoScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val user = state.userInfo.value
    var name by rememberSaveable(user?.id) { mutableStateOf(user?.name.orEmpty()) }
    var nickname by rememberSaveable(user?.id) { mutableStateOf(user?.nickname.orEmpty()) }
    var year by rememberSaveable(user?.id) { mutableStateOf(user?.birth_year?.toString().orEmpty()) }
    var month by rememberSaveable(user?.id) { mutableStateOf(user?.birth_month?.toString().orEmpty()) }
    val valid = name.isNotBlank() && name.length <= 20 && nickname.length <= 20 && isValidBirthMonth(year, month)
    Column(Modifier.fillMaxSize().imePadding()) {
        TmtnTopBar("기본 정보", onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("나를 소개해 주세요.", style = TmtnType.editorialHeadline, color = colors.onSurface)
            Text("이름과 생년월은 필수 정보예요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            TmtnTextField(name, { name = it.take(20) }, "이름 · 필수", supportingText = "1–20자", imeAction = ImeAction.Next)
            Text("생년월", style = TmtnType.label, color = colors.onSurface, modifier = Modifier.padding(top = 8.dp))
            ResponsiveFieldPair(first = {
                TmtnTextField(year, { year = it.filter(Char::isDigit).take(4) }, "태어난 연도", it, keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            }, second = {
                TmtnTextField(month, { month = it.filter(Char::isDigit).take(2) }, "월", it, keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            })
            Text("태어난 연도와 월까지만 입력해요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
            TmtnTextField(nickname, { nickname = it.take(20) }, "별명 · 선택", supportingText = "앱 화면에 표시돼요. 비워 두면 이름을 표시해요.", imeAction = ImeAction.Done)
            OnboardingErrorMessage(state.errorMessage.value)
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TmtnTonalButton("취소", onBack, modifier = Modifier.weight(1f), enabled = !state.isLoading.value)
            TmtnPrimaryButton(if (state.isLoading.value) "저장 중…" else "저장", {
                scope.launch { state.saveBasicInfo(name, nickname, year.toInt(), month.toInt()) }
            }, modifier = Modifier.weight(1f), enabled = valid, loading = state.isLoading.value)
        }
    }
}
