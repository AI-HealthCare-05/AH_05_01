package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.CompanionResponse
import com.tmtn.app.ui.common.DamArtwork
import com.tmtn.app.ui.common.damRepairLabel
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.CancellationException
import retrofit2.Response

/** Read-only: never award materials or infer stage zero from a failed request. */
internal class FirstDamState(
    private val fetch: suspend () -> Response<CompanionResponse> = { ApiClient.cardHomeApi.getCompanionStatus() },
) {
    var companion by mutableStateOf<CompanionResponse?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var failed by mutableStateOf(false)
        private set

    suspend fun refresh() {
        loading = true
        failed = false
        try {
            val response = fetch()
            val body = response.body()
            if (response.isSuccessful && body != null) companion = body else failed = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
}

// ⚠️ 2026-09-18 이식(UI/UX 핸드오프 FR01~08, PR #21 소스 기준) - 서버가 UNAVAILABLE
// (이미 첫 복구를 쓴 적 있거나 애초에 없는 계정)을 반환하면 이 레거시 경로로 옴 -
// 새 재료를 지급하지 않고 지금 댐 상태를 읽기 전용으로만 보여줌. onBack은
// FirstRepairScreen.kt와 같은 이유로 항상 null.
@Composable
internal fun LegacyFirstDamRoute(onContinue: () -> Unit) {
    val dam = remember { FirstDamState() }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) { dam.refresh() }
    FirstDamScreen(
        displayName = "",
        companion = dam.companion, loading = dam.loading, failed = dam.failed,
        onRetry = { retry++ },
        onBack = null,
        onContinue = onContinue,
    )
}

@Composable
internal fun FirstDamScreen(
    displayName: String,
    companion: CompanionResponse?,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onBack: (() -> Unit)?,
    onContinue: () -> Unit,
) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        TmtnTopBar("내 댐 첫 공개", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                (if (displayName.isBlank()) "나의 댐," else "${displayName}의 댐,") + "\n오늘부터 같이 손보자.",
                style = TmtnType.editorialHeadline, color = colors.onSurface,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Text("이미 있는 댐의 빈틈을 천천히 메워 가요.", style = TmtnType.body, color = colors.onSurface)
            when {
                companion != null -> {
                    val stage = companion.current_stage.coerceIn(0, 5)
                    DamArtwork(stage, Modifier.testTag("first-dam-art-$stage"), "내 댐 ${stage}단계")
                    Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${stage}단계 · ${damRepairLabel(stage)}", style = TmtnType.label, color = colors.onSurface)
                        Text("모은 재료 ${companion.total_materials}개", style = TmtnType.body, color = colors.onSurface)
                        if (companion.next_stage_threshold != null) {
                            Text(if (stage == 0) "첫 복구 단계까지 재료 ${companion.materials_needed_for_next}개가 더 필요해요."
                                else "다음 복구 단계까지 재료 ${companion.materials_needed_for_next}개가 더 필요해요.",
                                style = TmtnType.body, color = colors.onSurfaceVariant)
                        }
                    }
                    Text("틈튼이", style = TmtnType.label, color = colors.onSurface)
                    Text(if (companion.total_materials == 0) "오늘은 첫 재료를 모아 볼까?\n카드를 실천한 만큼 복구 흔적이 남을 거야."
                        else "이미 모은 재료는 그대로야.\n오늘의 실천도 여기서 이어 가자.",
                        style = TmtnType.body, color = colors.onSurface)
                }
                loading -> Row(Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = colors.primary, strokeWidth = 2.dp)
                    Text("내 댐을 준비하고 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                }
                failed -> Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("내 댐을 불러오지 못했어요.", style = TmtnType.title, color = colors.onSurface)
                    Text("카드는 먼저 고를 수 있어요. 댐은 나중에 댐 탭에서도 볼 수 있어요.",
                        style = TmtnType.body, color = colors.onSurfaceVariant)
                    TmtnOutlinedButton("다시 불러오기", onRetry)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            TmtnPrimaryButton("오늘 카드 만나기", onContinue)
        }
    }
}
