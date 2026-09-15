package com.tmtn.app.ui.dam

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.ApiClient
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.cardhome.MaterialIcon
import com.tmtn.app.ui.common.tmtnMaterialDrawable
import com.tmtn.app.ui.journal.completionDate
import com.tmtn.app.ui.onboarding.*
import com.tmtn.app.ui.theme.*
import kotlinx.coroutines.CancellationException

internal val damMaterialNames = linkedMapOf("WOOD" to "나뭇가지", "FIRE" to "받침돌", "EARTH" to "다짐흙", "METAL" to "새잎", "WATER" to "물길")

/** C03 1314:4937. Keep loading and unavailable distinct from an actual count of zero. */
@Composable
internal fun MaterialDetailScreen(element: String, onBack: () -> Unit) {
    var history by remember(element) { mutableStateOf<MaterialHistoryResponse?>(null) }
    var failed by remember(element) { mutableStateOf(false) }
    var reload by remember(element) { mutableIntStateOf(0) }
    LaunchedEffect(element, reload) {
        failed = false
        try {
            val response = ApiClient.cardHomeApi.getMaterialHistory(element)
            history = response.body().takeIf { response.isSuccessful }
            failed = history == null
        } catch (c: CancellationException) { throw c } catch (_: Exception) { failed = true }
    }
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar(damMaterialNames[element] ?: "재료", onBack)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("작은 실천이\n차곡차곡 모였어요.", style = TmtnType.headline, color = colors.onSurface)
            Image(painterResource(tmtnMaterialDrawable(element)), null, Modifier.fillMaxWidth().height(160.dp))
            when {
                failed -> DamLoadMessage(true) { reload++ }
                history == null -> DamLoadMessage(false) {}
                else -> {
                    val result = history!!
                    Text("모은 ${result.material_name} ${result.count}개", style = TmtnType.title, color = colors.onSurface)
                    if (result.recent_history.isEmpty()) {
                        Text("아직 모은 재료가 없어요. 실천을 마치면 이곳에 남아요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                    } else result.recent_history.forEach { card ->
                        Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(card.title, style = TmtnType.label, color = colors.onSurface)
                            Text(historyDate(card.completed_at), style = TmtnType.body, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("틈튼이", style = TmtnType.label, color = colors.onSurface)
                Text("같은 재료도 얼마든지 쓸모가 있어. 모인 만큼 댐의 다음 단계를 준비해.", style = TmtnType.body, color = colors.onSurface)
            }
            TmtnTonalButton("재료 도감으로", onBack)
        }
    }
}

/** C06 1314:5064: readable action titles, selectable filters, and a real detail destination. */
@Composable
internal fun CardCollectionScreen(onBack: () -> Unit, onOpenMaterials: () -> Unit) {
    val pages = rememberSaveableStateHolder()
    var filter by remember { mutableStateOf<String?>(null) }
    var collection by remember(filter) { mutableStateOf<CardCollectionResponse?>(null) }
    var failed by remember(filter) { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<CardHistoryItem?>(null) }
    LaunchedEffect(filter, reload) {
        failed = false
        try {
            val response = ApiClient.cardHomeApi.getCardCollection(filter)
            collection = response.body().takeIf { response.isSuccessful }
            failed = collection == null
        } catch (c: CancellationException) { throw c } catch (_: Exception) { failed = true }
    }
    if (selected != null) {
        BackHandler { selected = null }
        CompletedCardDetail(selected!!) { selected = null }
    } else pages.SaveableStateProvider("collection") {
        CollectionContent(collection, failed, filter, { filter = it }, { reload++ }, { selected = it }, onBack, onOpenMaterials)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CollectionContent(collection: CardCollectionResponse?, failed: Boolean, filter: String?, onFilter: (String?) -> Unit,
    onRetry: () -> Unit, onCard: (CardHistoryItem) -> Unit, onBack: () -> Unit, onMaterials: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar("완료한 카드첩", onBack)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("실천한 행동을\n한 장씩 모아뒀어요.", style = TmtnType.headline, color = colors.onSurface)
            collection?.let { Text("완료한 카드 ${it.total_count}장", style = TmtnType.body, color = colors.onSurfaceVariant) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (linkedMapOf<String?, String>(null to "전체") + damMaterialNames).forEach { (element, label) ->
                    Box(Modifier.background(if (filter == element) colors.navigationIndicator else colors.surface, RoundedCornerShape(50))
                        .selectable(filter == element, role = Role.Tab, onClick = { onFilter(element) })
                        .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = TmtnType.label, color = colors.onSurface)
                    }
                }
            }
            when {
                failed -> DamLoadMessage(true, onRetry)
                collection == null -> DamLoadMessage(false) {}
                collection.cards.isEmpty() -> {
                    Text(if (filter == null) "첫 실천을 마치면 한 장이 남아요." else "이 재료의 카드는 아직 없어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
                    if (filter != null) TmtnTonalButton("전체 카드 보기", { onFilter(null) })
                }
                else -> collection.cards.forEach { card ->
                    Row(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp))
                        .tmtnClickable(role = Role.Button, onClick = { onCard(card) }).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MaterialIcon(card.five_element, 44.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(card.title, style = TmtnType.label, color = colors.onSurface)
                            Text("${historyDate(card.completed_at)} · ${card.material_name}", style = TmtnType.body, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            TmtnTonalButton("재료별로 보기", onMaterials)
        }
    }
}

/** The history API has no fortune/location/target fields: never invent the missing card text. */
@Composable
private fun CompletedCardDetail(card: CardHistoryItem, onBack: () -> Unit) {
    var day by remember(card) { mutableStateOf<DayDetailResponse?>(null) }
    LaunchedEffect(card) {
        val date = completionDate(card.completed_at) ?: return@LaunchedEffect
        try {
            val response = ApiClient.recordApi.getDayDetail(date.toString())
            day = response.body()?.takeIf { response.isSuccessful && it.mission_title == card.title && it.element == card.five_element }
        } catch (c: CancellationException) { throw c } catch (_: Exception) { /* The known card remains readable offline. */ }
    }
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxSize()) {
        TmtnTopBar("완료 카드 자세히", onBack)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(24.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("오늘의 틈", style = TmtnType.label, color = colors.wood)
                Image(painterResource(tmtnMaterialDrawable(card.five_element)), null, Modifier.fillMaxWidth().height(120.dp))
                Text(card.title, style = TmtnType.headline, color = colors.onSurface)
                Text("${historyDate(card.completed_at)} · 실천 완료", style = TmtnType.body, color = colors.onSurfaceVariant)
                Text("${card.material_name} · ${card.domain_label}", style = TmtnType.label, color = colors.wood)
            }
            day?.memo?.takeIf { it.isNotBlank() }?.let { memo ->
                Text("함께 남긴 메모", style = TmtnType.label, color = colors.onSurface)
                Text(memo, style = TmtnType.body, color = colors.onSurface)
            }
            TmtnTonalButton("카드첩으로", onBack)
        }
    }
}

private fun historyDate(value: String): String = completionDate(value)?.let { "${it.monthValue}월 ${it.dayOfMonth}일" } ?: "날짜 확인 중"

@Composable
private fun DamLoadMessage(failed: Boolean, onRetry: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (failed) "기록을 불러오지 못했어요." else "기록을 불러오고 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
        if (failed) TmtnTonalButton("다시 불러오기", onRetry)
    }
}
