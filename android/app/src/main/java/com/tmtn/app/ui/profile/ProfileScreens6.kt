package com.tmtn.app.ui.profile

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.tmtn.app.ui.legal.LegalDocument

/** Figma F05 · 개인정보 · 내 데이터 (+ F13 기록만삭제 확인 다이얼로그 통합) */
@Composable
fun PrivacyDataScreen(
    state: ProfileState, scope: CoroutineScope, onBack: () -> Unit,
    onOpenTerms: (LegalDocument) -> Unit,
) {
    val colors = LocalTmtnColors.current
    var showDeleteRecordsDialog by state.confirmRecordDeletion

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "개인정보 · 내 데이터", onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("무엇을 저장하나요", style = TmtnType.label, color = colors.onSurface)
                DataCategoryRow("행동 기록", "완료 여부 · 시각 · 걸린 시간")
                DataCategoryRow("입력한 값", "생년월 · 성별 · 키 · 몸무게 · 주당 운동량")
                DataCategoryRow("계정", "이메일 · 로그인 기록")
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                ProfileListItem("내 데이터 내보내기", "CSV 파일로 받습니다", horizontalInset = 20.dp) { state.screen.value = ProfileScreenKey.EXPORT_DATA }
                ProfileListItem("기록만 삭제", "계정은 그대로 두고 기록을 지웁니다", horizontalInset = 20.dp) {
                    state.errorMessage.value = null
                    state.recordsDeletedDone.value = false
                    showDeleteRecordsDialog = true
                }
                ProfileListItem("개인정보 처리방침", null, horizontalInset = 20.dp) { onOpenTerms(LegalDocument.PRIVACY_NOTICE) }
                ProfileListItem("이용약관", null, horizontalInset = 20.dp) { onOpenTerms(LegalDocument.TERMS) }
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
            ) {
                Text("정보의 이용 목적은 항목별 동의 안내와 개인정보 처리방침에서 확인할 수 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            }
            if (state.recordsDeletedDone.value) {
                Text("기록을 지웠어요. 계정은 그대로 이용할 수 있어요.", style = TmtnType.body, color = colors.onSurface,
                    modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
            }
        }
    }

    // F13: 기록 삭제 확인 다이얼로그
    if (showDeleteRecordsDialog) {
        com.tmtn.app.ui.common.TmtnConfirmationDialog(
            title = "기록을 모두 지울까요?",
            message = "행동 기록, 재료, 댐 진행이 사라집니다. 계정과 이메일은 그대로 둡니다. 되돌릴 수 없습니다.",
            confirmLabel = "기록 지우기", cancelLabel = "그만두기",
            busy = state.isLoading.value, error = state.errorMessage.value,
            onConfirm = { scope.launch { if (state.deleteRecordsOnly()) showDeleteRecordsDialog = false } },
            onDismiss = { showDeleteRecordsDialog = false; state.errorMessage.value = null },
        )
    }
}

@Composable
private fun DataCategoryRow(title: String, sub: String) {
    val colors = LocalTmtnColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = TmtnType.caption, color = colors.onSurfaceVariant)
        Text(sub, style = TmtnType.body, color = colors.onSurface)
    }
}

/** Figma F14 · 내 데이터 내보내기 */
@Composable
fun ExportDataScreen(
    state: ProfileState, scope: CoroutineScope, onBack: () -> Unit,
    onSaveCsv: (fileName: String, content: String) -> Unit,
) {
    val colors = LocalTmtnColors.current
    var exportDone by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "내 데이터 내보내기", onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("내 기록을 파일로 받아요", style = TmtnType.headline, color = colors.onSurface)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                ProfileListItem("행동 기록", "완료 여부 · 시각 · 걸린 시간")
                ProfileListItem("입력한 값", "생년월 · 성별 · 키 · 몸무게 · 주당 운동량")
                ProfileListItem("계정 정보", "이메일 · 로그인 기록")
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
            ) {
                Text(
                    if (exportDone) "다운로드 폴더에 CSV 파일을 저장했어요." else "CSV 파일로 받을 수 있어요. 저장한 파일은 다운로드 폴더에서 찾아보세요.",
                    style = TmtnType.body, color = colors.onSurface,
                )
            }
            Text("건강과 관련된 입력값은 광고나 외부 제공에 쓰지 않습니다.", style = TmtnType.label, color = colors.onSurfaceVariant)
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TmtnPrimaryButton(
                text = if (state.isLoading.value) "파일 준비 중…" else if (exportDone) "다시 내보내기" else "CSV 파일 받기",
                enabled = !state.isLoading.value,
                onClick = {
                    exportDone = false
                    scope.launch {
                        state.exportMyData { fileName, content ->
                            onSaveCsv(fileName, content)
                            exportDone = true
                        }
                    }
                },
            )
        }
    }
}
