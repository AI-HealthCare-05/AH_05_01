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
    val result = ScorePercentilePresentation.fromCompositeScore(score)
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
                    if (large) Text(result.primaryLabel, style = TmtnType.display, color = colors.primary)
                    else Row(verticalAlignment = Alignment.Bottom) {
                        val number = if (result.isPreview) result.scoreNumber else result.position.toString()
                        Text(number, style = TmtnType.display.copy(fontSize = (if (number.length >= 3) 42 else 54).sp, lineHeight = 58.sp), color = colors.primary)
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
        // ⚠️ 2026-09-16 추가 - practice-score(초기 습관+실천+건강 종합) 최소 노출.
        // composite_score가 있을 때만 보여주고, null이면(초기 습관 정책 미확정 등)
        // 아무것도 안 보여줌 - 0이나 이전 값으로 대체하지 않음. 이 자리 배치는 최소
        // 연결용 - 정식 화면 위치·디자인은 별도 확인 필요.
        state.practiceScore.value?.composite_score?.let { compositeScore ->
            Text("생활습관 실천을 반영한 참고점수: ${compositeScore}점",
                style = TmtnType.caption, color = colors.onSurfaceVariant)
        }
        // ⚠️ 2026-09-16 문구 교체(강호님 "초기습관_산식과_모델표시_확정_v1" §4 기본 안내) -
        // 예전 "비진단용 참고 정보"는 문서가 명시한 정확한 안내 문구가 아니었음.
        Text("틈튼지수는 입력 정보와 생활습관 실천을 반영한 참고점수입니다. 건강 상태를 " +
            "진단하거나 질환의 발생 가능성을 뜻하지 않습니다.", style = TmtnType.caption, color = colors.onSurfaceVariant)
    }
    if (showMethod) ScoreMethodDialog(score) { showMethod = false }
}
