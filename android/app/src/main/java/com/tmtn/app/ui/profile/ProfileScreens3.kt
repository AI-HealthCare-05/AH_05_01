package com.tmtn.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnChip
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.tmtn.app.ui.legal.LegalDocument
import androidx.compose.material3.TextButton

private val MANDATORY_PURPOSES = listOf(
    "TERMS_OF_SERVICE" to "서비스 이용약관",
    "PRIVACY_POLICY" to "개인정보 수집 · 이용 동의",
    "AGE_OVER_14" to "만 14세 이상",
    "HEALTH_DATA_USAGE" to "건강정보 수집 · 이용",
)
private val OPTIONAL_PURPOSES = listOf(
    "LOCATION_DATA_USAGE" to ("위치정보 수집 · 이용" to "거리를 재는 미션에서 사용해요"),
    "HEALTH_REFERENCE_ANALYSIS" to ("틈튼지수 산출을 위한 분석" to "동의하지 않아도 챌린지는 그대로 이용할 수 있습니다"),
    "NOTIFICATION" to ("카드 · 미션 알림 받기" to "기기 알림 권한은 별도로 설정해요"),
)

/** Figma F10 · 동의 관리 (+ F11 선택 동의 철회 확인 다이얼로그 통합) */
@Composable
fun ConsentScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var withdrawTarget by state.pendingWithdrawal
    val consentByPurpose = state.consents.value.associateBy { it.purpose }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "동의 관리", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("필수 항목", style = TmtnType.label, color = colors.onSurface)
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                MANDATORY_PURPOSES.forEach { (purpose, label) ->
                    val document = LegalDocument.forPurpose(purpose)
                    ProfileListItem(label, agreedDateText(consentByPurpose[purpose]?.agreed_at),
                        horizontalInset = 20.dp,
                        onClick = document?.let { { state.openLegal(it) } })
                }
            }

            Text("선택 항목", style = TmtnType.label, color = colors.onSurface)
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                OPTIONAL_PURPOSES.forEach { (purpose, info) ->
                    val (label, sub) = info
                    val agreed = consentByPurpose[purpose]?.status == "AGREED"
                    val changeConsent: (Boolean) -> Unit = { checked ->
                        if (checked) {
                            scope.launch { state.agreeOptionalConsent(purpose); syncDeviceNotifications(context, state) }
                        } else {
                            state.errorMessage.value = null
                            withdrawTarget = purpose
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .toggleable(agreed, enabled = !state.isLoading.value, role = Role.Switch, onValueChange = changeConsent)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(label, style = TmtnType.label, color = colors.onSurface)
                            Text(sub, style = TmtnType.caption, color = colors.onSurfaceVariant)
                        }
                        Switch(
                            checked = agreed,
                            enabled = !state.isLoading.value,
                            onCheckedChange = null,
                        )
                    }
                    LegalDocument.forPurpose(purpose)?.let { document ->
                        TextButton(onClick = { state.openLegal(document) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text("${document.title} 보기", style = TmtnType.caption)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(20.dp),
            ) {
                Text("필수 항목은 서비스를 쓰는 동안 철회할 수 없습니다. 철회하시려면 계정을 삭제해야 합니다.", style = TmtnType.body, color = colors.onSurface)
            }
            Text("선택 항목은 언제든 켜고 끌 수 있습니다.", style = TmtnType.body, color = colors.onSurfaceVariant)
        }
    }

    // F11: 선택 동의 철회 확인
    val target = withdrawTarget
    if (target != null) {
        com.tmtn.app.ui.common.TmtnConfirmationDialog(
            title = "동의를 철회할까요?",
            message = "동의를 철회하면 관련 기능이 즉시 꺼집니다. 언제든 다시 켤 수 있습니다.",
            confirmLabel = "철회하기", busy = state.isLoading.value, error = state.errorMessage.value,
            onConfirm = { scope.launch {
                if (state.withdrawConsent(target)) {
                    withdrawTarget = null
                    syncDeviceNotifications(context, state)
                }
            } },
            onDismiss = { withdrawTarget = null; state.errorMessage.value = null },
        )
    }
}

private fun agreedDateText(agreedAt: String?): String {
    if (agreedAt == null) return ""
    return "${agreedAt.take(10)} 동의함"
}
