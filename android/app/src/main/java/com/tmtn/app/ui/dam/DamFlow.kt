package com.tmtn.app.ui.dam

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 시스템 뒤로가기 - 화면 안의 "←" 버튼과 동일하게.
    val previousScreen = previousScreenFor(screen)
    androidx.activity.compose.BackHandler(enabled = previousScreen != null) {
        previousScreen?.let { screen = it }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        runCatching { ApiClient.cardHomeApi.getCompanionStatus() }
            .getOrNull()?.let { response ->
                if (response.isSuccessful) companion = response.body()
            }
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
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
                element = selectedElement, scope = scope,
                onBack = { screen = DamScreen.ENCYCLOPEDIA },
            )
            DamScreen.COLLECTION -> CardCollectionScreen(scope = scope, onBack = { screen = DamScreen.HOME })
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        }
    }
}

/** Figma G01 · 댐 홈 */
@Composable
private fun DamHomeScreen(
    companion: CompanionResponse?,
    onOpenEncyclopedia: () -> Unit,
    onOpenStageGuide: () -> Unit,
    onOpenCollection: () -> Unit,
) {
    val colors = LocalTmtnColors.current

    Column(
        // ⚠️ 2026-09-06 QA(레이아웃) 반영: 스크롤 하단 패딩이 하단 탭바 높이(104dp)만큼
        // 없어서 마지막 콘텐츠(댐 카드 등)가 탭바 뒤로 잘려 들어갔음.
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp).padding(bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("집 · 댐", style = TmtnType.title, color = colors.onSurface)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(colors.surface, RoundedCornerShape(16.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // ⚠️ 2026-09-06 반영: 홍주님이 전달한 댐 5단계 그림을 실제로 붙임(문서
            // ASSETS_배치_전달서_2026-09-06.md 3장 참고). 캔버스 비율(1536×1024, 1.5:1)이
            // 슬롯(350×180dp, 1.94:1)이랑 안 맞아서 Fit + BottomCenter로 - Crop을 쓰면
            // 5단계(콘텐츠가 제일 큼)의 윗부분이 잘림. 5장 모두 바닥선이 같아서 이렇게
            // 두면 1→5단계 성장이 정확히 보임. current_stage가 0(아직 없음)이면 1단계로.
            val stage = (companion?.current_stage ?: 1).coerceIn(1, 5)
            Image(
                painter = painterResource(
                    when (stage) {
                        1 -> com.tmtn.app.R.drawable.dam_stage_1
                        2 -> com.tmtn.app.R.drawable.dam_stage_2
                        3 -> com.tmtn.app.R.drawable.dam_stage_3
                        4 -> com.tmtn.app.R.drawable.dam_stage_4
                        else -> com.tmtn.app.R.drawable.dam_stage_5
                    },
                ),
                contentDescription = "댐 성장 ${stage}단계",
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomCenter,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
        }

        if (companion != null) {
            val currentStageInfo = companion.stages.firstOrNull { it.stage_number == companion.current_stage }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${companion.current_stage}단계" + (currentStageInfo?.label?.let { " · $it" } ?: ""),
                        style = TmtnType.title, color = colors.onSurface,
                    )
                    if (companion.next_stage_threshold != null) {
                        Text("${companion.total_materials} / ${companion.next_stage_threshold}", style = TmtnType.body, color = colors.onSurfaceVariant)
                    }
                }
                if (companion.next_stage_threshold != null) {
                    val progress = (companion.total_materials.toFloat() / companion.next_stage_threshold).coerceIn(0f, 1f)
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(colors.outline, RoundedCornerShape(4.dp)))
                        Box(modifier = Modifier.fillMaxWidth(progress).height(8.dp).background(colors.onSurface, RoundedCornerShape(4.dp)))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (companion.materials_needed_for_next > 0) "다음 단계까지 재료 ${companion.materials_needed_for_next}개" else "최고 단계예요",
                        style = TmtnType.caption, color = colors.onSurfaceVariant,
                    )
                    Text(
                        "단계 안내 ›", style = TmtnType.caption, color = colors.onSurface,
                        modifier = Modifier.clickable { onOpenStageGuide() },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("모은 재료", style = TmtnType.label, color = colors.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // ⚠️ 2026-09-06 QA(P2) 반영: count > 0인 것만 걸러서 보여줬더니 5종
                    // 중 일부만 보여서 "무엇을 더 모아야 하는지" 전체 그림이 안 잡혔음
                    // (리포트: "나뭇가지·물길 칩이 렌더되지 않음, 둘 다 획득한 적이 있는데도").
                    // 필터를 없애고, 0개인 것도 회색 톤으로 계속 보여줌 - 수집 동기 유지.
                    companion.materials.forEach { m ->
                        val hasAny = m.count > 0
                        Row(
                            modifier = Modifier
                                .background(if (hasAny) colors.surface else colors.disabledContainer, RoundedCornerShape(999.dp))
                                .border(1.dp, colors.outlineVariant, RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MaterialIcon(element = m.element, size = 24.dp)
                            Text(
                                "${m.material_name} ${m.count}개", style = TmtnType.caption,
                                color = if (hasAny) colors.onSurface else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text("지금까지 모은 재료 ${companion.total_materials}개", style = TmtnType.caption, color = colors.onSurfaceVariant)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TmtnOutlinedButton(text = "재료 도감", onClick = onOpenEncyclopedia, modifier = Modifier.weight(1f))
            TmtnOutlinedButton(text = "틈튼 카드첩", onClick = onOpenCollection, modifier = Modifier.weight(1f))
        }
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
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
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                            .clickable { onOpenMaterial(element) }.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
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
                    "어떤 재료든 하나씩 쌓이면 댐은 자라.\n골고루 아니어도 괜찮아.",
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
        TmtnTopBar(title = "댐이 자라는 순서", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            companion?.stages?.forEach { stage ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${stage.stage_number}단계 · ${stage.label}", style = TmtnType.bodyLarge, color = colors.onSurface)
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
                Text("단계가 내려가는 일은 없습니다. 쉬어도 쌓인 재료는 그대로 남습니다.", style = TmtnType.body, color = colors.onSurfaceVariant)
            }
        }
    }
}

private fun formatHistoryDate(isoString: String): String {
    return try {
        val instant = java.time.Instant.parse(isoString)
        val zoned = instant.atZone(java.time.ZoneId.systemDefault())
        "%d. %d. %d. %s %d:%02d".format(
            zoned.year, zoned.monthValue, zoned.dayOfMonth,
            if (zoned.hour < 12) "오전" else "오후",
            if (zoned.hour % 12 == 0) 12 else zoned.hour % 12, zoned.minute,
        )
    } catch (e: Exception) {
        isoString.take(10)
    }
}

/** Figma G05 · 재료별 기록 상세 */
@Composable
private fun MaterialDetailScreen(element: String, scope: kotlinx.coroutines.CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    var history by remember(element) {
        mutableStateOf<com.tmtn.app.network.model.MaterialHistoryResponse?>(null)
    }

    LaunchedEffect(element) {
        runCatching { ApiClient.cardHomeApi.getMaterialHistory(element) }
            .getOrNull()?.let { if (it.isSuccessful) history = it.body() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = history?.material_name ?: "재료", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .background(colors.surface, RoundedCornerShape(999.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MaterialIcon(element = element, size = 32.dp)
                    Text(history?.material_name ?: "", style = TmtnType.label, color = colors.onSurface)
                }
                Text("${history?.count ?: 0}개", style = TmtnType.display, color = colors.onSurface)
                Text(history?.domain_label ?: "", style = TmtnType.body, color = colors.onSurfaceVariant)
            }

            Text("언제 받았나", style = TmtnType.label, color = colors.onSurface)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
            ) {
                history?.recent_history?.forEach { item ->
                    Column(
                        modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(item.title, style = TmtnType.label, color = colors.onSurface)
                        Text(formatHistoryDate(item.completed_at), style = TmtnType.caption, color = colors.onSurfaceVariant)
                    }
                }
            }
            Text("최근 5개만 보여드립니다.", style = TmtnType.caption, color = colors.onSurfaceVariant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
            ) {
                Text("재료는 완료한 날에 하나씩 쌓입니다. 쉬어도 줄지 않습니다.", style = TmtnType.body, color = colors.onSurface)
            }
        }
    }
}

/** Figma G06 · 틈튼 카드첩 */
@Composable
private fun CardCollectionScreen(scope: kotlinx.coroutines.CoroutineScope, onBack: () -> Unit) {
    val colors = LocalTmtnColors.current
    var selectedFilter by remember { mutableStateOf<String?>(null) } // null = 전체
    var collection by remember { mutableStateOf<com.tmtn.app.network.model.CardCollectionResponse?>(null) }

    LaunchedEffect(selectedFilter) {
        runCatching { ApiClient.cardHomeApi.getCardCollection(selectedFilter) }
            .getOrNull()?.let { if (it.isSuccessful) collection = it.body() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TmtnTopBar(title = "카드첩", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("모은 카드 ${collection?.total_count ?: 0}장", style = TmtnType.label, color = colors.onSurface)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CollectionFilterChip(label = "전체", selected = selectedFilter == null) { selectedFilter = null }
                ELEMENT_ORDER.forEach { element ->
                    val label = when (element) {
                        "WOOD" -> "나뭇가지"; "FIRE" -> "받침돌"; "WATER" -> "물길"
                        "EARTH" -> "다짐흙"; else -> "새잎"
                    }
                    CollectionFilterChip(label = label, selected = selectedFilter == element) { selectedFilter = element }
                }
            }

            val cards = collection?.cards ?: emptyList()
            if (cards.isEmpty()) {
                Text("아직 완료한 카드가 없어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        cards.chunked(2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowItems.forEach { card -> CollectionCardTile(card) }
                            }
                        }
                    }
                }
            }
            Text("완료한 카드만 모입니다. 건너뛴 날은 남지 않습니다.", style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun CollectionFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalTmtnColors.current
    Row(
        modifier = Modifier
            .background(colors.surface, RoundedCornerShape(999.dp))
            .border(if (selected) 3.dp else 1.dp, if (selected) colors.secondary else colors.outline, RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = TmtnType.label, color = colors.onSurface)
    }
}

@Composable
private fun CollectionCardTile(card: com.tmtn.app.network.model.CardHistoryItem) {
    val colors = LocalTmtnColors.current
    Column(
        modifier = Modifier
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MaterialIcon(element = card.five_element, size = 40.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${card.material_name} 1개", style = TmtnType.label, color = colors.onSurface)
            Text(card.domain_label, style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        Text(card.completed_at.take(10).substring(5).replace("-", ". ") + ".", style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
}
