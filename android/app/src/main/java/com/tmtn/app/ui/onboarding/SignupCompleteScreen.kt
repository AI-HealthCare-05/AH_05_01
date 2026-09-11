package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.common.TmtnMascot
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

@Composable
fun SignupCompleteScreen(onContinue: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(12.dp))
        TmtnMascot(R.drawable.beaver_wave, "가입을 반기는 비버", Modifier.fillMaxWidth().height(208.dp))
        Text("가입이 완료됐어요", style = TmtnType.headline, color = colors.onSurface, textAlign = TextAlign.Center)
        Text("맞춤 미션을 위해 신체·운동 정보를 입력해 주세요.", style = TmtnType.body,
            color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        TmtnPrimaryButton("필수 정보 입력하기", onClick = onContinue)
    }
}
