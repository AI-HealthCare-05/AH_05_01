package com.tmtn.app.ui.reference

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.network.model.ScoreInputsResponse
import com.tmtn.app.network.model.WeeklyReportResponse
import com.tmtn.app.ui.theme.*
import java.time.LocalDate

@Composable
internal fun NewsInputs(load: EditorialLoad<ScoreInputsResponse>, area: String, onEdit: () -> Unit, onRetry: () -> Unit) {
    val colors = LocalTmtnColors.current
    val title = when (area) {
        "physical" -> "내 몸의 입력값"
        "diabetes" -> "당뇨 기사에 보낸 자료"
        "hypertension" -> "혈압 기사에 보낸 자료"
        "lifestyle" -> "내가 보낸 운동 습관"
        else -> "내가 보낸 정보"
    }
    Column(Modifier.fillMaxWidth().padding(top = 26.dp).testTag("news-inputs-$area"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (load is EditorialLoad.Ready && area in setOf("physical", "lifestyle")) {
            ScoreRule(true)
            Text(title, style = TmtnType.title, color = ColorBrandForest, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            val facts = scoreInputFacts(load.value, area)
            if (area == "physical") {
                facts.filter { it.label in setOf("키", "몸무게") }.forEach { InfoRow(it.label, it.value) }
            } else {
                val minutes = listOf(load.value.cardio_low_min, load.value.cardio_moderate_min, load.value.cardio_vigorous_min)
                val total = if (minutes.all { it != null && it >= 0 }) minutes.sumOf { it!!.toLong() }.toString() + "분 / 주" else "일부 정보 미입력"
                InfoRow("유산소 합계", total)
                InfoRow("근력운동", facts.first().value)
            }
            NewsDisclosure(if (area == "physical") "기본 정보 더 읽기" else "강도별 운동 정보", "최근 저장한 입력값", "$area-inputs") {
                facts.filter { if (area == "physical") it.label !in setOf("키", "몸무게") else it.label != "근력운동" }
                    .forEach { InfoRow(it.label, it.value) }
                NewsInputFootnote()
                ScoreActionRow(if (area == "physical") "신체 정보 확인·수정" else "운동 정보 확인·수정", onClick = onEdit)
            }
        } else {
            NewsDisclosure(title, when (load) {
                is EditorialLoad.Ready -> "신체·운동 정보 ${scoreInputFacts(load.value, area).size}개 항목"
                EditorialLoad.Loading -> "자료를 불러오고 있어요"
                EditorialLoad.Failed -> "자료를 불러오지 못했어요"
            }, "$area-inputs") {
                when (load) {
                    EditorialLoad.Loading -> NewsLoading("입력 정보를 불러오고 있어요")
                    EditorialLoad.Failed -> NewsRetry("입력 정보를 불러오지 못했어요.", onRetry)
                    is EditorialLoad.Ready -> {
                        scoreInputFacts(load.value, area).forEach { InfoRow(it.label, it.value) }
                        NewsInputFootnote()
                        ScoreActionRow("입력 정보 확인·수정", onClick = onEdit)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsInputFootnote() {
    Text("최근 저장한 정보예요. 개별 항목이 결과에 미친 영향의 크기를 뜻하지는 않아요.",
        style = TmtnType.caption, color = LocalTmtnColors.current.onSurfaceVariant)
}

@Composable
internal fun NewsRecords(load: EditorialLoad<WeeklyReportResponse>, onRetry: () -> Unit) {
    val colors = LocalTmtnColors.current
    Column(Modifier.fillMaxWidth().padding(top = 28.dp).testTag("news-life"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScoreRule(true)
        Text("생활면", style = TmtnType.label, color = ColorBrandForest, modifier = Modifier.padding(top = 8.dp))
        when (load) {
            EditorialLoad.Loading -> NewsLoading("내 기록의 소식을 불러오고 있어요")
            EditorialLoad.Failed -> NewsRetry("생활면을 불러오지 못했어요.", onRetry)
            is EditorialLoad.Ready -> {
                val news = recordNews(load.value)
                if (news == null) NewsRetry("기록을 확인하지 못했어요.", onRetry)
                else {
                    Text(news.headline, style = TmtnType.headline, color = colors.onSurface, modifier = Modifier.semantics { heading() })
                    Text(news.detail, style = TmtnType.bodyLarge, color = ColorBrandForest)
                    Image(painterResource(if (news.empty) R.drawable.beaver_rest else if (news.evening) R.drawable.score_news_evening else R.drawable.beaver_newspaper),
                        null, Modifier.fillMaxWidth().then(if (news.evening) Modifier.aspectRatio(1.5f) else Modifier.height(160.dp)),
                        contentScale = ContentScale.Fit)
                    Text(scorePeriod(load.value.start_date, load.value.end_date) + " · 실천 완료 기록",
                        style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun NewsPracticeWeek(load: EditorialLoad<WeeklyReportResponse>, onRetry: () -> Unit) {
    val colors = LocalTmtnColors.current
    val large = LocalDensity.current.fontScale * LocalTmtnTextScale.current > 1.35f
    Column(Modifier.fillMaxWidth().padding(top = 28.dp).testTag("news-practice"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScoreRule(true)
        Text("실천이 남긴 날짜", style = TmtnType.title, color = ColorBrandForest, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        when (load) {
            EditorialLoad.Loading -> NewsLoading("실천 기록을 불러오고 있어요")
            EditorialLoad.Failed -> NewsRetry("실천 기록을 불러오지 못했어요.", onRetry)
            is EditorialLoad.Ready -> {
                val report = load.value
                val days = report.days.mapNotNull { item -> runCatching { LocalDate.parse(item.date) }.getOrNull()?.let { it to item.status } }
                    .distinctBy { it.first }.sortedBy { it.first }.takeLast(7)
                if (recordNews(report) == null || days.isEmpty()) NewsRetry("날짜별 기록을 확인하지 못했어요.", onRetry)
                else {
                    Text("최근 ${report.total_days}일 중 ${report.completed_count}일 실천했어요.", style = TmtnType.bodyLarge, color = colors.onSurface)
                    if (large) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        days.forEach { (date, status) -> InfoRow("${date.monthValue}월 ${date.dayOfMonth}일", practiceStatus(status)) }
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        days.forEach { (date, status) ->
                            val complete = status == "COMPLETED"
                            Column(Modifier.weight(1f).background(if (complete) colors.secondaryContainer else colors.surface, RoundedCornerShape(8.dp))
                                .padding(vertical = 10.dp).clearAndSetSemantics {
                                    contentDescription = "${date.monthValue}월 ${date.dayOfMonth}일, ${practiceStatus(status)}"
                                }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(listOf("월", "화", "수", "목", "금", "토", "일")[date.dayOfWeek.value - 1], style = TmtnType.label, color = colors.onSurfaceVariant)
                                Text(date.dayOfMonth.toString(), style = TmtnType.bodyLarge, color = colors.onSurface)
                                Text(if (complete) "✓" else if (status == "REST") "쉼" else "·", style = TmtnType.label, color = if (complete) colors.secondary else colors.onSurfaceVariant)
                            }
                        }
                    }
                    Text("실천 기록을 돌아보는 생활면이에요. 위 지수의 계산 결과와는 별도로 보여줘요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

private fun practiceStatus(status: String) = when (status) {
    "COMPLETED" -> "실천 완료"
    "REST" -> "쉬었어요"
    "INCOMPLETE" -> "완료 기록 없음"
    else -> "상태 확인 필요"
}

@Composable
private fun NewsLoading(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = ColorBrandForest)
        Text(text, style = TmtnType.body, color = LocalTmtnColors.current.onSurfaceVariant)
    }
}

@Composable
private fun NewsRetry(text: String, onRetry: () -> Unit) {
    Text(text, style = TmtnType.body, color = LocalTmtnColors.current.onSurfaceVariant)
    ScoreActionRow("다시 불러오기", onClick = onRetry)
}
