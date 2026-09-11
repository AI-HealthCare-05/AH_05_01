package com.tmtn.app.ui.reference

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmtn.app.R
import com.tmtn.app.ui.theme.*

@Composable
internal fun ScoreNewspaperSummary(state: ReferenceState, onOpenExerciseInfo: () -> Unit, onOpenArea: (String) -> Unit,
    waist: WaistEstimateUi) {
    val score = state.score.value ?: return
    val colors = LocalTmtnColors.current
    val result = ScorePercentilePresentation.fromCurrent(score.peerCompositeScore, score.canShowOverall)
    val large = LocalDensity.current.fontScale * LocalTmtnTextScale.current > 1.15f
    var showMethod by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("틈튼지수", style = TmtnType.headline, color = colors.onSurface)
        Text(if (result?.isPreview != false) "내 몸과 생활,\n한눈에." else "비버 마을 속\n내 자리를 찾았어요.", style = TmtnType.headline, color = colors.onSurface, modifier = Modifier.semantics { heading() })
        val number: @Composable () -> Unit = {
            Column(Modifier.clearAndSetSemantics {
                contentDescription = result?.reading ?: "정보를 더 입력해 주세요"
            }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result != null) {
                    Text(if (result.isPreview) "나의 틈튼지수" else "100명 중", style = TmtnType.body, color = ColorBrandForest)
                    if (large) Text(result.primaryLabel, style = TmtnType.display, color = colors.secondary)
                    else Row(verticalAlignment = Alignment.Bottom) {
                        val number = if (result.isPreview) result.scoreNumber else result.position.toString()
                        Text(number, style = TmtnType.display.copy(fontSize = (if (number.length >= 3) 42 else 54).sp, lineHeight = 58.sp), color = colors.secondary)
                        Text(if (result.isPreview) "점" else "번째쯤", style = TmtnType.label, color = colors.onSurface, modifier = Modifier.padding(start = 5.dp, bottom = 7.dp))
                    }
                } else Text("정보를 더\n입력해 주세요", style = TmtnType.title, color = colors.onSurface)
            }
        }
        if (large) number()
        Box(Modifier.fillMaxWidth()) {
            Image(painterResource(R.drawable.score_news_guide), null, Modifier.fillMaxWidth().aspectRatio(1.5f), contentScale = ContentScale.Fit)
            if (!large) Box(Modifier.fillMaxWidth(.52f).padding(top = 8.dp)) { number() }
        }
        Row(Modifier.fillMaxWidth().background(colors.surface).tmtnClickable { state.openDetail() }
            .semantics { contentDescription = "내 틈튼일보 펼치기" }.padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!large) Image(painterResource(R.drawable.score_news_bundle), null, Modifier.width(62.dp).height(70.dp), contentScale = ContentScale.Fit)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("오늘의 틈튼일보", style = TmtnType.caption, color = ColorBrandForest)
                Text("내 소식 펼쳐보기", style = TmtnType.bodyLarge, color = colors.onSurface)
            }
            ScoreArrow(true)
        }
        WaistEstimateSummary(waist, onOpen = { state.openWaist() })
        ScoreRule()
        ScoreActionRow("운동 정보 업데이트", "유산소·근력운동", onOpenExerciseInfo)
        ScoreActionRow("입력 정보 확인", "신체 정보 · 운동 정보") { state.openInputs() }
        ScoreActionRow("이 지수에 대하여") { showMethod = true }
        Text("비진단용 참고 정보", style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
    if (showMethod) ScoreMethodDialog(score) { showMethod = false }
}
