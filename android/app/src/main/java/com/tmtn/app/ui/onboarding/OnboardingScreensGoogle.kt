package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.auth.GoogleSignInHelper
import com.tmtn.app.ui.common.TmtnSheetDialog
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** Figma 1314:2470 · story → a deliberate choice of account provider. */
@Composable
fun AuthChoiceScreen(
    onGoogle: () -> Unit, onEmail: () -> Unit, onLogin: () -> Unit, onBack: () -> Unit,
    googleConfigured: Boolean = GoogleSignInHelper.isConfigured(), busy: Boolean = false,
) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar("내 댐 기억하기", onBack = if (busy) null else onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)) {
            Text("다음에 와도\n여기서 이어 가자.", style = TmtnType.headline, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))
            Text("계정을 연결하면 댐과 실천 기록을 이어 볼 수 있어요.",
                style = TmtnType.caption, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            Image(painterResource(R.drawable.beaver_wave), contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(180.dp))
            Spacer(Modifier.height(24.dp))
            Text("작은 실천, 틈튼이와 함께해요.",
                style = TmtnType.body, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GoogleContinueButton(onGoogle, enabled = googleConfigured && !busy)
            if (!googleConfigured) Text("지금은 이메일로 계속할 수 있어요.",
                style = TmtnType.caption, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            TmtnPrimaryButton("이메일로 시작하기", onEmail, enabled = !busy)
            if (!busy) TmtnTextButton("이미 계정이 있어요", onLogin)
        }
    }
}

@Composable
internal fun GoogleContinueButton(onClick: () -> Unit, enabled: Boolean = true) {
    val colors = LocalTmtnColors.current
    FilledTonalButton(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.surface,
            contentColor = colors.onSurface, disabledContainerColor = colors.surface,
            disabledContentColor = colors.onSurfaceVariant)) {
        Image(painterResource(R.drawable.google_g_logo), null, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text("Google로 계속하기", style = TmtnType.label)
    }
}

@Composable
internal fun GoogleLinkDialog(email: String, busy: Boolean, error: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalTmtnColors.current
    TmtnSheetDialog(onDismiss = onDismiss, canDismiss = !busy) {
      val sheet = this
      Surface(Modifier.fillMaxWidth().align(Alignment.BottomCenter).tmtnSheetMotion(), color = colors.surface,
          shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("이어서 쓸 계정이 있어요", style = TmtnType.title, color = colors.onSurface)
            Text(email, style = TmtnType.label, color = colors.onSurface)
            Text("이 이메일로 만든 계정에 Google을 연결할까요? 기존 댐과 실천 기록은 그대로 이어집니다.",
                style = TmtnType.body, color = colors.onSurfaceVariant)
            if (error != null) OnboardingErrorMessage(error)
            TmtnPrimaryButton("기존 계정에 연결하기", onConfirm, enabled = !busy, loading = busy)
            if (!busy) TmtnTextButton("다른 방법으로 로그인", { sheet.dismiss(onDismiss) }, Modifier.align(Alignment.CenterHorizontally))
        }
      }
    }
}
