package com.tmtn.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnChip
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTextButton
import com.tmtn.app.ui.onboarding.TmtnTextField
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Figma F19 · 앱 정보 · 오픈소스 라이선스 */
@Composable
fun AppInfoScreen(onBack: () -> Unit, onOpenTerms: () -> Unit) {
    val colors = LocalTmtnColors.current
    val openSourceLibs = listOf(
        "Retrofit" to "Apache License 2.0",
        "Coroutines" to "Apache License 2.0",
        "Jetpack Compose" to "Apache License 2.0",
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "앱 정보", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                ProfileListItem("버전", "1.0.0") { }
                ProfileListItem("서비스 이용약관", null) { onOpenTerms() }
                ProfileListItem("개인정보 처리방침", null) { onOpenTerms() }
            }

            Text("오픈소스 라이선스", style = TmtnType.label, color = colors.onSurface)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                openSourceLibs.forEach { (name, license) ->
                    Column {
                        Text(name, style = TmtnType.body, color = colors.onSurface)
                        Text(license, style = TmtnType.caption, color = colors.onSurfaceVariant)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text("이 앱은 의료기기가 아닙니다. 틈튼지수는 진단이 아니라 참고 지표입니다.", style = TmtnType.body, color = colors.onSurface)
            }
        }
    }
}

/** Figma F20 · 도움말 상세 (예시 FAQ 하나) */
@Composable
fun HelpDetailScreen(onBack: () -> Unit, onInquiry: () -> Unit) {
    val colors = LocalTmtnColors.current
    var feedback by remember { mutableStateOf<Boolean?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "도움말", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("카드는 어떻게 정해지나요?", style = TmtnType.headline, color = colors.onSurface)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "매일 아침 세 장을 준비합니다. 세 장은 뒷면이 모두 같아서 고르기 전에는 내용을 알 수 없습니다.",
                    style = TmtnType.body, color = colors.onSurface,
                )
                Text(
                    "최근에 적게 한 항목이 조금 더 자주 나옵니다. 완전히 무작위로 나오는 것은 아닙니다.",
                    style = TmtnType.body, color = colors.onSurface,
                )
                Text(
                    "고른 카드는 그날 바꿀 수 없습니다. 상황이 맞지 않으면 오늘은 하기 어려워요를 눌러 실행 방법만 바꿀 수 있습니다.",
                    style = TmtnType.body, color = colors.onSurface,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("이 답이 도움이 되었나요?", style = TmtnType.bodyLarge, color = colors.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TmtnChip(text = "도움이 됐어요", selected = feedback == true, onClick = { feedback = true })
                    TmtnChip(text = "잘 모르겠어요", selected = feedback == false, onClick = { feedback = false })
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TmtnTextButton(text = "문의 남기기", onClick = onInquiry)
        }
    }
}

private val INQUIRY_TOPICS = listOf(
    "CARD_CHALLENGE" to "카드 · 챌린지",
    "RECORD_DAM" to "기록 · 댐",
    "ACCOUNT_LOGIN" to "계정 · 로그인",
    "OTHER" to "그 밖에",
)

/** Figma F21 · 문의 남기기 */
@Composable
fun InquiryScreen(state: ProfileState, scope: CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    var topic by remember { mutableStateOf("CARD_CHALLENGE") }
    var content by remember { mutableStateOf("") }
    var includeDeviceInfo by remember { mutableStateOf(true) }
    val submitted = state.inquirySubmitted.value

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "문의", onBack = onBack)

        if (submitted) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("문의를 보냈어요", style = TmtnType.headline, color = colors.onSurface, textAlign = TextAlign.Center)
                Text(
                    "영업일 기준 2일 안에 답변드립니다. 답변은 가입한 메일 주소로 갑니다.",
                    style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                TmtnPrimaryButton(text = "닫기", onClick = onBack)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("무엇에 대한 문의인가요?", style = TmtnType.label, color = colors.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    INQUIRY_TOPICS.forEach { (value, label) ->
                        TmtnChip(text = label, selected = topic == value, onClick = { topic = value })
                    }
                }

                TmtnTextField(
                    value = content, onValueChange = { if (it.length <= 1000) content = it },
                    label = "내용", supportingText = "어떤 일이 있었는지 적어 주세요.",
                )
                Text(
                    "${content.length} / 1000", style = TmtnType.caption, color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeDeviceInfo, onCheckedChange = { includeDeviceInfo = it })
                    Column(modifier = Modifier.weight(1f)) {
                        Text("기기 정보 함께 보내기", style = TmtnType.label, color = colors.onSurface)
                        Text("기종 · 앱 버전 · 오류 코드만 보냅니다", style = TmtnType.caption, color = colors.onSurfaceVariant)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Text("영업일 기준 2일 안에 답변드립니다. 답변은 가입한 메일 주소로 갑니다.", style = TmtnType.body, color = colors.onSurface)
                }

                TmtnPrimaryButton(
                    text = "보내기",
                    onClick = {
                        val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · v1.0.0"
                        scope.launch { state.submitInquiry(topic, content, includeDeviceInfo, deviceInfo) }
                    },
                    enabled = content.isNotBlank(),
                )
            }
        }
    }
}
