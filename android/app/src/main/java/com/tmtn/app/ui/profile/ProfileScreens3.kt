package com.tmtn.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.AccessibilityUpdateRequest
import com.tmtn.app.ui.onboarding.TmtnChip
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val MANDATORY_PURPOSES = listOf(
    "TERMS_OF_SERVICE" to "서비스 이용약관",
    "PRIVACY_POLICY" to "개인정보 처리방침",
    "AGE_OVER_14" to "만 14세 이상",
    "HEALTH_DATA_USAGE" to "건강정보 수집 · 이용",
)
private val OPTIONAL_PURPOSES = listOf(
    "LOCATION_DATA_USAGE" to ("위치정보 수집 · 이용" to "달리기·걷기 미션 중에만 거리를 잽니다"),
    "HEALTH_REFERENCE_ANALYSIS" to ("틈튼지수 산출을 위한 분석" to "동의하지 않아도 챌린지는 그대로 이용할 수 있습니다"),
    "NOTIFICATION" to ("알림 받기" to "생활시간에 맞춰 알려드립니다"),
)

/** Figma F10 · 동의 관리 (+ F11 선택 동의 철회 확인 다이얼로그 통합) */
@Composable
fun ConsentScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    var withdrawTarget by remember { mutableStateOf<String?>(null) }
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
                    ProfileListItem(label, agreedDateText(consentByPurpose[purpose]?.agreed_at)) { }
                }
            }

            Text("선택 항목", style = TmtnType.label, color = colors.onSurface)
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                OPTIONAL_PURPOSES.forEach { (purpose, info) ->
                    val (label, sub) = info
                    val agreed = consentByPurpose[purpose]?.status == "AGREED"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(label, style = TmtnType.label, color = colors.onSurface)
                            Text(sub, style = TmtnType.caption, color = colors.onSurfaceVariant)
                        }
                        Switch(
                            checked = agreed,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    scope.launch { state.agreeOptionalConsent(purpose) }
                                } else {
                                    withdrawTarget = purpose
                                }
                            },
                        )
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
        AlertDialog(
            onDismissRequest = { withdrawTarget = null },
            title = { Text("동의를 철회할까요?", style = TmtnType.bodyLarge, color = colors.onSurface) },
            text = {
                Text(
                    "동의를 철회하면 관련 기능이 즉시 꺼집니다. 언제든 다시 켤 수 있습니다.",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                )
            },
            confirmButton = {
                Text(
                    "철회하기", style = TmtnType.label, color = colors.error,
                    modifier = Modifier.clickable {
                        scope.launch { state.withdrawConsent(target) }
                        withdrawTarget = null
                    }.padding(8.dp),
                )
            },
            dismissButton = {
                Text(
                    "취소", style = TmtnType.label, color = colors.primary,
                    modifier = Modifier.clickable { withdrawTarget = null }.padding(8.dp),
                )
            },
        )
    }
}

private fun agreedDateText(agreedAt: String?): String {
    if (agreedAt == null) return ""
    return "${agreedAt.take(10)} 동의함"
}

/** Figma F12 · 접근성 설정 */
@Composable
fun AccessibilityScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    val accessibility = state.accessibility.value

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "접근성", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("글자 크기", style = TmtnType.label, color = colors.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("NORMAL" to "보통", "LARGE" to "크게", "EXTRA_LARGE" to "아주 크게").forEach { (value, label) ->
                    TmtnChip(
                        text = label,
                        selected = (accessibility?.preferred_text_scale_hint ?: "NORMAL") == value,
                        onClick = { scope.launch { state.updateAccessibility(AccessibilityUpdateRequest(preferred_text_scale_hint = value)) } },
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("미리보기", style = TmtnType.caption, color = colors.onSurfaceVariant)
                Text("점심 먹고 8분 걷기", style = TmtnType.bodyLarge, color = colors.onSurface)
                Text("짧게 걸어도 오늘 한 걸음은 남아.", style = TmtnType.body, color = colors.onSurfaceVariant)
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                AccessibilityToggleRow(
                    "큰 글자 모드", "버튼과 터치 영역을 56까지 키웁니다",
                    accessibility?.large_controls ?: false,
                ) { scope.launch { state.updateAccessibility(AccessibilityUpdateRequest(large_controls = it)) } }
                AccessibilityToggleRow(
                    "동작 줄이기", "화면 전환 효과를 줄입니다",
                    accessibility?.reduced_motion ?: false,
                ) { scope.launch { state.updateAccessibility(AccessibilityUpdateRequest(reduced_motion = it)) } }
                AccessibilityToggleRow(
                    "고대비", "글자와 배경의 차이를 키웁니다",
                    accessibility?.senior_mode ?: false,
                ) { scope.launch { state.updateAccessibility(AccessibilityUpdateRequest(senior_mode = it)) } }
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)).padding(20.dp),
            ) {
                Text("기능과 정보의 순서는 어떤 설정에서도 같습니다.", style = TmtnType.body, color = colors.onSurface)
            }
        }
    }
}

@Composable
private fun AccessibilityToggleRow(title: String, sub: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalTmtnColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = TmtnType.label, color = colors.onSurface)
            Text(sub, style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
