package com.tmtn.app.ui.dam

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.CompanionResponse
import com.tmtn.app.ui.cardhome.MaterialIcon
import com.tmtn.app.ui.onboarding.TmtnOutlinedButton
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

private enum class DamScreen { HOME, ENCYCLOPEDIA, STAGE_GUIDE, MATERIAL_DETAIL, COLLECTION }

private val ELEMENT_ORDER = listOf("WOOD", "FIRE", "EARTH", "METAL", "WATER")

private fun previousScreenFor(screen: DamScreen): DamScreen? = when (screen) {
    DamScreen.HOME -> null
    DamScreen.ENCYCLOPEDIA -> DamScreen.HOME
    DamScreen.STAGE_GUIDE -> DamScreen.HOME
    DamScreen.MATERIAL_DETAIL -> DamScreen.ENCYCLOPEDIA
    DamScreen.COLLECTION -> DamScreen.HOME
}

/** G01/G02/G03 "댐" 탭 전체. 화면 이동은 다른 흐름과 동일한 패턴(상태값으로 전환). */
@Composable
fun DamFlow() {
    val colors = LocalTmtnColors.current
    var screen by remember { mutableStateOf(DamScreen.HOME) }
    var companion by remember { mutableStateOf<CompanionResponse?>(null) }
    var selectedElement by remember { mutableStateOf("WOOD") }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 시스템 뒤로가기 - 화면 안의 "←" 버튼과 동일하게.
    val previousScreen = previousScreenFor(screen)
    androidx.activity.compose.BackHandler(enabled = previousScreen != null) {
        previousScreen?.let { screen = it }
    }

    LaunchedEffect(reload) {
        isLoading = true
        loadFailed = false
        try {
            val response = ApiClient.cardHomeApi.getCompanionStatus()
            if (response.isSuccessful && response.body() != null) companion = response.body()
            else loadFailed = true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadFailed = true
        } finally { isLoading = false }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (loadFailed && companion == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("내 댐을 불러오지 못했어요.", style = TmtnType.title, color = colors.onSurface)
                Text("연결을 확인한 뒤 다시 열어주세요.", style = TmtnType.body, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp))
                com.tmtn.app.ui.onboarding.TmtnPrimaryButton("다시 불러오기", { reload++ })
            }
        } else when (screen) {
            DamScreen.HOME -> DamHomeScreen(
                companion = companion,
                onOpenEncyclopedia = { screen = DamScreen.ENCYCLOPEDIA },
                onOpenStageGuide = { screen = DamScreen.STAGE_GUIDE },
                onOpenCollection = { screen = DamScreen.COLLECTION },
            )
            DamScreen.ENCYCLOPEDIA -> MaterialEncyclopediaScreen(
                onBack = { screen = DamScreen.HOME },
                onOpenMaterial = { element ->
                    selectedElement = element
                    screen = DamScreen.MATERIAL_DETAIL
                },
            )
            DamScreen.STAGE_GUIDE -> StageGuideScreen(companion = companion, onBack = { screen = DamScreen.HOME })
            DamScreen.MATERIAL_DETAIL -> MaterialDetailScreen(
                element = selectedElement,
                onBack = { screen = DamScreen.ENCYCLOPEDIA },
            )
            DamScreen.COLLECTION -> CardCollectionScreen(onBack = { screen = DamScreen.HOME }, onOpenMaterials = { screen = DamScreen.ENCYCLOPEDIA })
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        }
    }
}

/** Figma C01 1314:4840; existing companion values, repaired-dam artwork. */
@Composable
internal fun DamHomeScreen(
    companion: CompanionResponse?,
    onOpenEncyclopedia: () -> Unit,
    onOpenStageGuide: () -> Unit,
    onOpenCollection: () -> Unit,
) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxSize()) {
        Text("내 댐", style = TmtnType.label, color = colors.onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp))
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("빈틈이 메워지는\n나의 댐.", style = TmtnType.headline, color = colors.onSurface)
            if (companion != null) {
                val stage = companion.current_stage.coerceIn(0, 5)
                Text("${stage}단계 · ${com.tmtn.app.ui.common.damRepairLabel(stage)}", style = TmtnType.body, color = colors.onSurface)
                com.tmtn.app.ui.common.DamArtwork(stage)
                Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("모은 재료 ${companion.total_materials}개", style = TmtnType.title, color = colors.onSurface)
                    Text(if (companion.next_stage_threshold != null) "다음 단계까지 ${companion.materials_needed_for_next}개"
                        else "틈을 모두 메웠어요. 앞으로의 실천도 기록에 남아요.",
                        style = TmtnType.body, color = colors.onSurfaceVariant)
                }
                companion.next_stage_threshold?.takeIf { it > 0 }?.let { threshold ->
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (companion.total_materials.toFloat() / threshold).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(10.dp), color = colors.onSurface,
                        trackColor = colors.outlineVariant, gapSize = 0.dp, drawStopIndicator = {},
                    )
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ELEMENT_ORDER.forEach { element ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            MaterialIcon(element, size = 48.dp)
                            Text("${companion.materials.firstOrNull { it.element == element }?.count ?: 0}",
                                style = TmtnType.caption, color = colors.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Text("내 댐의 기록을 불러오고 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            }
            DamMenuRow("모은 재료 살펴보기", "모든 종류가 함께 댐의 재료가 돼요.", onOpenEncyclopedia)
            DamMenuRow("완료한 카드첩", "내가 실천한 행동을 다시 펼쳐봐요.", onOpenCollection)
            com.tmtn.app.ui.onboarding.TmtnTonalButton("복구 단계 보기", onOpenStageGuide)
        }
    }
}

@Composable
private fun DamMenuRow(title: String, body: String, onClick: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))
        .clickable(onClickLabel = title, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = TmtnType.label, color = colors.onSurface)
        Text(body, style = TmtnType.body, color = colors.onSurfaceVariant)
    }
}

/** Figma G02 · 재료 도감 */
@Composable
private fun MaterialEncyclopediaScreen(onBack: () -> Unit, onOpenMaterial: (String) -> Unit) {
    val colors = LocalTmtnColors.current
    val materials = listOf(
        "WOOD" to ("나뭇가지" to "움직임 · 유산소"),
        "FIRE" to ("받침돌" to "근력"),
        "WATER" to ("물길" to "수분"),
        "EARTH" to ("다짐흙" to "생활리듬"),
        "METAL" to ("새잎" to "식사·기록"),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "재료 도감", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("행동의 종류에 따라 다른 재료가 쌓입니다.", style = TmtnType.body, color = colors.onSurfaceVariant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                materials.forEach { (element, info) ->
                    val (name, domain) = info
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
                            .clickable { onOpenMaterial(element) }.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MaterialIcon(element, size = 46.dp)
                        androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = TmtnType.label, color = colors.onSurface)
                            Text(domain, style = TmtnType.caption, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
            ) {
                Text(
                    "어떤 재료든 댐의 빈틈을 메우는 데 쓰여요.",
                    style = TmtnType.body, color = colors.onSurface,
                )
            }
        }
    }
}

/** Figma G03 · 댐 단계 안내 */
@Composable
private fun StageGuideScreen(companion: CompanionResponse?, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "댐 복구 단계", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("남아 있는 댐을\n한 곳씩 이어가요.", style = TmtnType.headline, color = colors.onSurface)
            com.tmtn.app.ui.common.DamArtwork(companion?.current_stage ?: 0)
            companion?.stages?.forEach { stage ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${stage.stage_number}단계 · ${com.tmtn.app.ui.common.damRepairLabel(stage.stage_number)}", style = TmtnType.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
                        if (stage.completed) {
                            Text("완료", style = TmtnType.caption, color = colors.onSurfaceVariant)
                        }
                    }
                    Text("재료 ${stage.threshold}개부터", style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text("쉬어 가도 메운 자리는 그대로예요. 다음 실천부터 이어가요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            }
        }
    }
}
