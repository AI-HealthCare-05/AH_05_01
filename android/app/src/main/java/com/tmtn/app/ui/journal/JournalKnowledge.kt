package com.tmtn.app.ui.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.BuildConfig
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.theme.*
import java.net.URI
import java.time.LocalDate

internal fun journalSourceUrlAllowed(raw: String?): Boolean = runCatching {
    val url = URI(raw ?: return false)
    url.scheme == "https" && url.host in setOf("www.foodsafetykorea.go.kr", "health.kdca.go.kr", "www.mohw.go.kr", "health.seoulmc.or.kr", "www.nhs.uk", "www.who.int", "www.cdc.gov", "www.niddk.nih.gov") &&
        url.rawUserInfo == null && url.port in listOf(-1, 443)
}.getOrDefault(false)

internal fun journalArticles(response: JournalEditorialResponse?, section: String?, allowPreview: Boolean = false): List<JournalKnowledgeArticle> {
    if (response?.schema_version != "tmtn-journal-editorial-v1" || response.preview == null) return emptyList()
    if (response.preview && !(allowPreview && BuildConfig.DEBUG)) return emptyList()
    if (runCatching { LocalDate.parse(response.service_date) }.isFailure) return emptyList()
    return response.sections.orEmpty().filter { it.status == "ready" && (section == null || it.section == section) }
        .flatMap { slot -> slot.articles.orEmpty().filter { it.section == slot.section } }
        .filter { article ->
            val source = article.source
            // Retired copy stays hidden while older servers are being updated.
            article.id != "movement.equivalence" &&
                article.section in listOf("movement_column", "table_column", "daily_column") && !article.id.isNullOrBlank() &&
                !article.title.isNullOrBlank() && !article.text.isNullOrBlank() && !article.revision.isNullOrBlank() &&
                article.content_sha256?.matches(Regex("[a-f0-9]{64}")) == true && article.numeric_slot != null &&
                source != null && !source.id.isNullOrBlank() && !source.title.isNullOrBlank() &&
                !source.publisher.isNullOrBlank() && !source.locator.isNullOrBlank() && !source.edition.isNullOrBlank() &&
                journalSourceUrlAllowed(source.url) && runCatching { LocalDate.parse(source.checked_on) }.isSuccess
        }.distinctBy { it.id }
}

/** 서버가 고른 호별 목록에도 원문·해시·공개 조건 검증을 동일하게 적용합니다. */
internal fun journalIssueArticles(response: JournalEditorialResponse?, weekly: Boolean, reviewPreview: Boolean = false): List<JournalKnowledgeArticle> {
    response ?: return emptyList()
    val date = runCatching { LocalDate.parse(response.service_date) }.getOrNull() ?: return emptyList()
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    val chosen = if (weekly) response.weekly_articles else response.daily_articles
    if (weekly && chosen != null && response.weekly_issue_start != monday.toString()) return emptyList()
    val pool = if (chosen == null) journalArticles(response, null, reviewPreview) else {
        val groups = chosen.groupBy { it.section }.map { (key, rows) -> JournalReadingSection(key, "ready", rows) }
        journalArticles(response.copy(sections = groups), null, reviewPreview)
    }
    val seed = if (weekly) monday else date
    val ordered = if (chosen != null) pool else pool.sortedBy {
        java.security.MessageDigest.getInstance("SHA-256").digest("$seed:${it.id}".toByteArray()).joinToString("") { b -> "%02x".format(b.toInt() and 255) }
    }
    return ordered.distinctBy { it.topic?.takeIf(String::isNotBlank) ?: it.id }.take(if (weekly) 2 else 1)
}

@Composable
fun JournalReadingColumns(
    editorial: JournalLoad<JournalEditorialResponse>?,
    onRetry: () -> Unit,
    weekly: Boolean = false,
    reviewPreview: Boolean = false,
) {
    val colors = LocalTmtnColors.current
    when (editorial) {
        JournalLoad.Loading -> Text("읽을거리를 펼치고 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
        JournalLoad.Failed -> Column {
            Text("읽을거리를 불러오지 못했어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            TextButton(onClick = onRetry) { Text("읽을거리 다시 불러오기", style = TmtnType.label) }
        }
        else -> {
            val response = (editorial as? JournalLoad.Ready)?.value
            val articles = journalIssueArticles(response, weekly, reviewPreview)
            if (articles.isEmpty()) Text("새로운 읽을거리를 준비하고 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            else Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                articles.forEachIndexed { index, article ->
                    if (index > 0) HorizontalDivider()
                    JournalKnowledgeArticleView(article)
                }
            }
        }
    }
}

@Composable
fun JournalKnowledgeArticleView(article: JournalKnowledgeArticle) {
    val colors = LocalTmtnColors.current
    val source = article.source ?: return
    var sourceOpen by remember(article.id, article.content_sha256) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("knowledge-${article.id}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(when (article.section) { "table_column" -> "식탁 읽을거리 · 일반 지식"; "daily_column" -> "하루 돌보기 · 일반 지식"; else -> "생활습관 · 움직임" }, style = TmtnType.label, color = colors.onSurfaceVariant)
        Text(article.title.orEmpty(), style = TmtnType.title, color = colors.onSurface, modifier = Modifier.semantics { heading() })
        Text(article.text.orEmpty(), style = TmtnType.body, color = colors.onSurface)
        Text(source.publisher.orEmpty(), style = TmtnType.caption, color = colors.onSurfaceVariant)
        TextButton(onClick = { sourceOpen = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("source-${article.id}")) {
            Text("출처와 적용 범위 보기", style = TmtnType.label)
        }
    }
    if (sourceOpen) JournalSourceDialog(article) { sourceOpen = false }
}

@Composable
private fun JournalSourceDialog(article: JournalKnowledgeArticle, onDismiss: () -> Unit) {
    val source = article.source ?: return
    val uriHandler = LocalUriHandler.current
    var openFailed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 글의 출처", style = TmtnType.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(source.title.orEmpty(), style = TmtnType.bodyLarge)
                Text("${source.publisher}\n${source.edition}\n${source.locator}", style = TmtnType.body)
                Text("출처 확인일 · ${source.checked_on}", style = TmtnType.caption)
                Text("공식 자료를 바탕으로 정리한 일반 정보예요. 내 식사 기록이나 질병 결과를 설명하는 글은 아니에요.", style = TmtnType.body)
                article.allowed_claim_scope?.takeIf { it.isNotBlank() }?.let { Text(it, style = TmtnType.caption) }
                if (article.audience_min_age != null) Text("자료 적용 연령 · 만 ${article.audience_min_age}세" + (article.audience_max_age?.let { "~${it}세" } ?: " 이상"), style = TmtnType.caption)
                if (article.id == "movement.replace_sitting")
                    Text("이 문장의 근거는 만 19~64세 성인 대상 지침이에요.", style = TmtnType.body)
                if (openFailed) Text("원문을 열지 못했어요. 인터넷 연결과 브라우저를 확인해 주세요.", style = TmtnType.body)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                openFailed = !journalSourceUrlAllowed(source.url) || runCatching { uriHandler.openUri(source.url!!) }.isFailure
            }) { Text("공식 원문 열기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
internal fun JournalRelatedReading(editorial: JournalLoad<JournalEditorialResponse>?, domain: String?, reviewPreview: Boolean = false) {
    val response = (editorial as? JournalLoad.Ready)?.value ?: return
    val articles = journalRelatedArticles(response, domain, reviewPreview)
    if (articles.isEmpty()) return
    var expanded by remember(domain, articles.map { it.id }) { mutableStateOf(false) }
    val label = if (domain == "diabetes") "당뇨" else "혈압"
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("함께 읽는 $label 이야기 · 일반 지식", style = TmtnType.label)
    }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        articles.forEach { JournalKnowledgeArticleView(it) }
    }
}

internal fun journalRelatedArticles(response: JournalEditorialResponse, domain: String?, reviewPreview: Boolean = false): List<JournalKnowledgeArticle> {
    if (domain !in setOf("diabetes", "hypertension")) return emptyList()
    val related = response.related_readings?.get(domain).orEmpty()
    val sections = related.groupBy { it.section }.map { (key, rows) -> JournalReadingSection(key, "ready", rows) }
    return journalArticles(response.copy(sections = sections), null, reviewPreview).take(2)
}

@Composable
internal fun JournalRecordEvidence() {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) { Text("이 기록은 어디서 왔나요?", style = TmtnType.label) }
    if (expanded) AlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text("이번 호의 기록 기준", style = TmtnType.title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("이번 호 기간에 완료 시각이 남아 있는 카드만 실천 기록에 담아요. 날짜는 한국 시간을 기준으로 나눠요.", style = TmtnType.body)
            Text("쉬어가기는 직접 선택한 날만 표시해요. 기록이 없는 날을 쉬었다고 판단하지 않아요.", style = TmtnType.body)
            Text("완료한 카드만으로 운동 강도나 혈압·혈당의 변화를 알 수는 없어요. 식탁과 움직임 기사는 별도의 일반 지식이에요.", style = TmtnType.body)
        } },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("확인") } },
    )
}
