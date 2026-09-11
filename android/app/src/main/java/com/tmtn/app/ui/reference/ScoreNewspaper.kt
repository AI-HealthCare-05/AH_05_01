package com.tmtn.app.ui.reference

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmtn.app.R
import com.tmtn.app.network.model.TuntunScorePeerV2Response
import com.tmtn.app.ui.onboarding.TmtnTopBar
import com.tmtn.app.ui.theme.*
import kotlinx.coroutines.launch

internal val NewsPaper = Color(0xFFFFFEFA)
internal val newsAreas = listOf("overall" to "종합", "physical" to "신체", "diabetes" to "당뇨", "hypertension" to "고혈압", "lifestyle" to "생활습관")

@Composable
internal fun ScoreNewspaperScreen(
    state: ReferenceState,
    initialArea: String = "overall",
    peerPositions: List<ScorePeerPositionUi> = emptyList(),
) {
    val score = state.score.value ?: return
    val colors = LocalTmtnColors.current
    var area by rememberSaveable { mutableStateOf(initialArea.takeIf { candidate -> newsAreas.any { it.first == candidate } } ?: "overall") }
    var showMethod by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val reduced = rememberTmtnReducedMotion()
    val large = LocalDensity.current.fontScale * LocalTmtnTextScale.current > 1.35f
    Column(Modifier.fillMaxSize().background(colors.surface)) {
        TmtnTopBar("내 틈튼일보", { state.goBack() }, trailing = {
            TextButton({ showMethod = true }, Modifier.heightIn(min = 48.dp)) {
                Text("읽는 법", style = TmtnType.label, color = ColorBrandForest)
            }
        })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 8.dp).background(NewsPaper)
            .padding(horizontal = if (large) 16.dp else 20.dp).padding(top = 20.dp, bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TMTN DAILY", style = TmtnType.label, color = ColorBrandForest)
                if (!large) Text("오직 나를 위한 신문", style = TmtnType.label, color = ColorBrandForest)
            }
            Text("틈튼일보", style = if (large) TmtnType.headline else TmtnType.display.copy(fontSize = 42.sp * LocalTmtnTextScale.current),
                color = colors.onSurface, modifier = Modifier.padding(top = 10.dp, bottom = 16.dp).semantics { heading() })
            ScoreRule(true)
            NewsTabs(area) { area = it }
            Spacer(Modifier.height(24.dp))
            val component = score.components.firstOrNull { it.componentKey == area }
            val result = if (area == "overall") ScorePercentilePresentation.fromCurrent(score.peerCompositeScore, score.canShowOverall)
                else ScorePercentilePresentation.fromCurrent(component?.peerPercentile, component?.hasResult == true)
            val peer = peerPositions.firstOrNull { it.key == area }
            val label = newsAreas.first { it.first == area }.second
            val position = peer?.positionFromHigherIndex ?: result?.takeUnless { it.isPreview }?.position
            val scoreValue = result?.takeIf { peer == null && it.isPreview }?.scoreNumber
            val missing = position == null && scoreValue == null
            // Data switches immediately. Only opacity bridges the page turn; numbers never count up.
            var reveal by remember { mutableStateOf(true) }
            LaunchedEffect(area) { reveal = false; withFrameNanos { }; reveal = true }
            val opacity by animateFloatAsState(if (reveal || reduced) 1f else .8f,
                tween(if (reduced) 0 else 160, easing = TmtnMotion.EaseOut), label = "newspaper article")
            Column(Modifier.fillMaxWidth().graphicsLayer { alpha = opacity }.testTag("news-article")) {
                Text("${(newsAreas.indexOfFirst { it.first == area } + 1).toString().padStart(2, '0')} · $label 편",
                    style = TmtnType.label, color = ColorBrandForest)
                Text(newsHeadline(area), style = TmtnType.headline, color = colors.onSurface,
                    modifier = Modifier.padding(top = 14.dp).semantics { heading() })
                NewsResultArtwork(position, scoreValue, area == "overall")
                if (peer != null) Text(peer.groupLabel + " · " + peer.referenceLabel, style = TmtnType.caption, color = colors.onSurfaceVariant)
                val explanation = when {
                    missing && area == "overall" -> "종합 지수는 2개 이상 영역이 있어야 표시해요. 입력 정보를 확인해 주세요."
                    missing -> "계산할 정보가 부족해요. 입력 정보를 확인해 주세요."
                    area == "overall" && score.isPartialScore -> score.coverageLabel + ". 정보가 없는 영역은 제외했어요."
                    else -> newsExplanation(area)
                }
                Text(explanation, style = TmtnType.body, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                if (missing) ScoreActionRow("입력 정보 확인", onClick = { state.openInputs() })
                COMPONENT_GUIDANCE[component?.componentKey]?.takeIf { component?.available == true }?.let { guidance ->
                    NewsDisclosure("$label 기사 해설", "결과를 읽는 데 필요한 안내", key = "$area-guidance") {
                        Text(guidance, style = TmtnType.body, color = colors.onSurfaceVariant)
                    }
                }
            }
            when (area) {
                "overall" -> {
                    NewsRecords(state.editorial.weekly.value, onRetry = { scope.launch { state.editorial.loadWeekly() } })
                    NewsInputs(state.editorial.inputs.value, area, { state.openInputs() }, { scope.launch { state.editorial.loadInputs() } })
                }
                "physical", "diabetes", "hypertension" -> NewsInputs(state.editorial.inputs.value, area,
                    { state.openInputs() }, { scope.launch { state.editorial.loadInputs() } })
                "lifestyle" -> {
                    NewsInputs(state.editorial.inputs.value, area, { state.openInputs() }, { scope.launch { state.editorial.loadInputs() } })
                    NewsPracticeWeek(state.editorial.weekly.value, { scope.launch { state.editorial.loadWeekly() } })
                }
            }
            val next = newsAreas[(newsAreas.indexOfFirst { it.first == area } + 1) % newsAreas.size]
            Spacer(Modifier.height(24.dp))
            ScoreRule()
            ScoreActionRow("${next.second} 편 이어 읽기", "다음 기사") {
                area = next.first
                scope.launch { if (reduced) scroll.scrollTo(0) else scroll.animateScrollTo(0, tween(220, easing = TmtnMotion.EaseOut)) }
            }
            ScoreRule(true)
            Text("TMTN DAILY · 오늘의 한 장", style = TmtnType.caption, color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp))
        }
    }
    if (showMethod) ScoreMethodDialog(score) { showMethod = false }
}

private fun newsHeadline(area: String) = when (area) {
    "physical" -> "내 몸의 정보가\n한 장의 소식으로."
    "diabetes" -> "당뇨 지수,\n차근차근 읽어요."
    "hypertension" -> "혈압 지수,\n차근차근 읽어요."
    "lifestyle" -> "나의 운동 습관,\n한 장에 담았어요."
    else -> "내 몸과 습관,\n오늘의 한 장."
}

private fun newsExplanation(area: String) = when (area) {
    "physical" -> "입력한 신체 정보로 계산한 참고 지수예요. 실제 체력 측정 결과와는 달라요."
    "diabetes" -> "당뇨 영역의 참고 지수예요. 앞으로 질환이 생길 확률을 뜻하지는 않아요."
    "hypertension" -> "고혈압 영역의 참고 지수예요. 혈압계로 측정한 혈압과는 다른 정보예요."
    "lifestyle" -> "입력한 유산소·근력운동 정보를 함께 살펴봤어요."
    else -> "신체와 생활습관, 네 영역을 함께 살펴본 결과예요."
}

@Composable
private fun NewsTabs(selected: String, onSelect: (String) -> Unit) {
    val colors = LocalTmtnColors.current
    val large = LocalDensity.current.fontScale * LocalTmtnTextScale.current > 1.15f
    Row(Modifier.fillMaxWidth().then(if (large) Modifier.horizontalScroll(rememberScrollState()) else Modifier).selectableGroup()) {
        newsAreas.forEach { (key, label) ->
            val interaction = remember { MutableInteractionSource() }
            val weight = when (key) { "lifestyle" -> 1.3f; "hypertension" -> 1.15f; else -> 1f }
            Column(Modifier.then(if (large) Modifier else Modifier.weight(weight)).tmtnPressFeedback(interaction)
                .selectable(selected == key, interactionSource = interaction, indication = ripple(), role = Role.Tab, onClick = { onSelect(key) })
                .heightIn(min = 52.dp).then(if (large) Modifier.padding(horizontal = 15.dp) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(label, style = TmtnType.label, color = if (key == selected) ColorBrandForest else colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).background(if (selected == key) ColorBrandForest else colors.outlineVariant))
            }
        }
    }
}

@Composable
internal fun NewsResultArtwork(position: Int?, scoreValue: String? = null, overall: Boolean = true) {
    val scale = LocalDensity.current.fontScale * LocalTmtnTextScale.current
    val colors = LocalTmtnColors.current
    val number = scoreValue ?: position?.toString()
    val suffix = if (scoreValue != null) "점" else "번째쯤"
    val caption = if (scoreValue != null) "틈튼지수" else "100명 중"
    val description = if (scoreValue != null) "틈튼지수 ${scoreValue}점" else if (position != null) "100명 중 약 ${position}번째"
        else if (overall) "정보를 더 입력해 주세요" else "아직 확인할 결과가 없어요"
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("news-result").semantics(mergeDescendants = true) {
        contentDescription = description
        liveRegion = LiveRegionMode.Polite
    }) {
        val artWidth = maxWidth
        if (scale > 1.15f) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (number != null) {
                    Text(caption, style = TmtnType.body, color = ColorBrandForest, modifier = Modifier.padding(top = 16.dp))
                    Text(number + suffix, style = TmtnType.display, color = colors.secondary)
                } else Text(description, style = TmtnType.title, modifier = Modifier.padding(top = 16.dp))
                Image(painterResource(R.drawable.beaver_newspaper), null, Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Fit)
            }
        } else {
            Image(painterResource(R.drawable.score_news_frontpage), null,
                Modifier.fillMaxWidth().aspectRatio(1.5f), contentScale = ContentScale.Fit)
            Column(Modifier.offset(x = artWidth * .13f, y = artWidth / 1.5f * .495f).width(artWidth * .33f)) {
                Text(if (number != null) caption else "이번 소식은", style = TmtnType.label.copy(fontSize = (artWidth.value * .032f).coerceAtLeast(11f).sp,
                    lineHeight = 14.sp), color = ColorBrandForest)
                if (number != null) Row(verticalAlignment = Alignment.Bottom) {
                    Text(number, style = TmtnType.display.copy(fontSize = (artWidth.value * if (number.length >= 3) .10f else .13f).sp,
                        lineHeight = (artWidth.value * .135f).sp, letterSpacing = (-1).sp), color = colors.secondary)
                    Text(suffix, style = TmtnType.label.copy(fontSize = (artWidth.value * .038f).sp, lineHeight = 17.sp),
                        color = colors.onSurface, modifier = Modifier.padding(start = 3.dp, bottom = 4.dp))
                } else Text("입력 대기 중", style = TmtnType.label.copy(fontSize = (artWidth.value * .048f).sp), color = ColorBrandForest)
            }
        }
    }
}

@Composable
internal fun NewsDisclosure(title: String, subtitle: String?, key: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    val colors = LocalTmtnColors.current
    val reduced = rememberTmtnReducedMotion()
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        ScoreRule()
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).tmtnClickable { expanded = !expanded }
            .semantics { stateDescription = if (expanded) "펼쳐짐" else "접힘" }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = TmtnType.bodyLarge, color = ColorBrandForest)
                if (subtitle != null) Text(subtitle, style = TmtnType.caption, color = colors.onSurfaceVariant)
            }
            Text(if (expanded) "−" else "+", style = TmtnType.title, color = ColorBrandForest, modifier = Modifier.padding(start = 10.dp))
        }
        AnimatedVisibility(expanded,
            enter = expandVertically(tween(if (reduced) 0 else 220, easing = TmtnMotion.EaseOut)) + fadeIn(tween(if (reduced) 0 else 160)),
            exit = shrinkVertically(tween(if (reduced) 0 else 180, easing = TmtnMotion.EaseOut)) + fadeOut(tween(if (reduced) 0 else 120))) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}
