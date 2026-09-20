package com.tmtn.app.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tmtn.app.network.model.*
import com.tmtn.app.ui.theme.*
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

internal fun verifiedActivityComparison(snapshot: PersonalXaiSnapshot): PersonalActivityComparison? {
    val activity = verifiedActivityData(snapshot.activity_comparison) ?: return null
    if (activity.input_revision != snapshot.input_revision || activity.reference_date != snapshot.reference_date ||
        snapshot.domains.orEmpty().any { it.reference_group != activity.group_key }) return null
    return activity
}

internal fun verifiedActivityData(candidate: PersonalActivityComparison?): PersonalActivityComparison? {
    val activity = candidate ?: return null
    if (activity.status != "ready" || activity.reference_version != "knhanes-activity-2019-2021-weighted-v1" ||
        activity.source_sha256 != "9f90ba20624d0afac93fbbb4968ce9e0226946892bf518574b4f4bb35f8ab787" ||
        activity.input_revision.isNullOrBlank() || runCatching { LocalDate.parse(activity.reference_date) }.isFailure ||
        activity.group_key !in listOf("19-39:1", "19-39:2", "40-64:1", "40-64:2", "65+:1", "65+:2") ||
        activity.source_kind != "knhanes_population_survey" || activity.years != listOf(2019, 2020, 2021) ||
        activity.group_label.isNullOrBlank() || activity.cards.isNullOrEmpty()) return null
    val cards = activity.cards
    if (cards.map { it.key }.distinct().size != cards.size || cards.any {
        val strength = it.key == "strength_days_week"
        it.key !in listOf("leisure_aerobic_moderate_equivalent_min_week", "strength_days_week") ||
            it.unit != (if (strength) "일" else "분") ||
            it.mean?.isFinite() != true || it.value?.isFinite() != true || it.delta?.isFinite() != true ||
            (it.n ?: 0) < 30 || (it.mean ?: -1.0) < 0 || (it.value ?: -1.0) < 0 ||
            (strength && ((it.mean ?: 6.0) > 5 || (it.value ?: 6.0) > 5)) ||
            abs((it.value ?: 0.0) - (it.mean ?: 0.0) - (it.delta ?: 0.0)) > 1e-9 ||
            it.text.isNullOrBlank() || it.unit_note.isNullOrBlank()
    }) return null
    return activity
}

internal fun activityAmount(card: PersonalActivityCard, personal: Boolean): String =
    if (personal && card.topcoded == true) "주 5일 이상"
    else "주 ${if (personal) card.value_display else card.mean_display}${card.unit}"

@Composable
internal fun JournalActivityComparison(activity: PersonalActivityComparison, practice: JournalPracticeContext? = null, onGoMission: (() -> Unit)? = null) {
    val colors = LocalTmtnColors.current
    val uriHandler = LocalUriHandler.current
    var sourceOpen by remember(activity.reference_version, activity.input_revision) { mutableStateOf(false) }
    var sourceFailed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("personal-activity-comparison"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("내 움직임, 또래와 나란히", style = TmtnType.title, color = colors.onSurface, modifier = Modifier.semantics { heading() })
        Text("${activity.group_label} · ${activity.source_label}", style = TmtnType.caption, color = colors.onSurfaceVariant)
        val surveyDate = runCatching { OffsetDateTime.parse(activity.survey_recorded_at).atZoneSameInstant(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy.M.d")) }.getOrNull()
        if (surveyDate != null) Text("$surveyDate 운동 설문 기준", style = TmtnType.caption, color = colors.onSurfaceVariant)
        activity.cards.orEmpty().forEach { card ->
            HorizontalDivider()
            Text(card.title.orEmpty(), style = TmtnType.label, color = colors.onSurface)
            Text(card.text.orEmpty(), style = TmtnType.body, color = colors.onSurface)
            val maximum = maxOf(card.mean ?: 0.0, card.value ?: 0.0, 1.0)
            listOf("내 설문" to (card.value ?: 0.0), "또래 평균" to (card.mean ?: 0.0)).forEach { (label, value) ->
                val amount = activityAmount(card, label == "내 설문")
                Column(Modifier.clearAndSetSemantics { contentDescription = "$label, $amount" }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = TmtnType.caption, color = colors.onSurfaceVariant)
                        Text(amount, style = TmtnType.label, color = colors.onSurface)
                    }
                    Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFFE4E7EB))) {
                        if (value > 0) Box(Modifier.fillMaxWidth((value / maximum).toFloat()).fillMaxHeight().background(if (label == "내 설문") Color(0xFF2F4A3A) else Color(0xFF949C96)))
                    }
                }
            }
            Text(card.unit_note.orEmpty(), style = TmtnType.caption, color = colors.onSurfaceVariant)
            JournalActivityHint(card, practice)
        }
        Text(activity.scope_note.orEmpty(), style = TmtnType.caption, color = colors.onSurfaceVariant)
        if (practice != null) JournalPracticeCoach(practice, onGoMission)
        TextButton(onClick = { sourceOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("또래 비교 기준 보기", style = TmtnType.label) }
    }
    if (sourceOpen) AlertDialog(onDismissRequest = { sourceOpen = false },
        title = { Text("같은 기준으로 나란히 봐요", style = TmtnType.title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${activity.source_label}의 ${activity.group_label} 응답을 사용했어요.", style = TmtnType.body)
            Text(activity.method_note.orEmpty(), style = TmtnType.body)
            activity.cards.orEmpty().forEach { Text("${it.label} · 응답 ${it.n}명\n${it.unit_note}", style = TmtnType.caption) }
            Text("이 비교는 운동 설문을 나란히 보는 자료예요. 아래 SHAP는 건강 모델의 계산 과정을 설명해요.", style = TmtnType.body)
            Text("입력 기준일 · ${activity.reference_date}", style = TmtnType.caption)
            TextButton(onClick = {
                sourceFailed = runCatching { uriHandler.openUri("https://knhanes.kdca.go.kr/knhanes/main.do") }.isFailure
            }) { Text("국민건강영양조사 원문 보기", style = TmtnType.label) }
            if (sourceFailed) Text("지금은 원문을 열지 못했어요. 잠시 후 다시 눌러 주세요.", style = TmtnType.caption)
        } }, confirmButton = { TextButton(onClick = { sourceOpen = false }) { Text("확인") } })
}

internal val personalFeatureOrder = listOf("age_years", "sex_code", "height_cm", "weight_kg", "leisure_aerobic_moderate_equivalent_min_week", "strength_days_week")
private val personalLabels = listOf("나이", "성별", "키", "몸무게", "유산소 활동 시간", "근력운동 일수")
internal const val personalDisplayEpsilon = 1e-8
internal fun personalDirectionLabel(value: Double): String =
    if (abs(value) <= personalDisplayEpsilon) "표시 기준에서 차이 없음" else if (value > 0) "높이는 쪽" else "낮추는 쪽"

internal fun verifiedPersonalSnapshot(state: JournalLoad<PersonalXaiResponse>?): PersonalXaiSnapshot? {
    val response = (state as? JournalLoad.Ready)?.value ?: return null
    val snapshot = response.snapshot ?: return null
    if (response.status != "ready" || snapshot.status != "ready" || snapshot.is_mock != false ||
        snapshot.schema_version != "tmtn-personal-xai-v1" || snapshot.snapshot_id.isNullOrBlank() || snapshot.input_revision.isNullOrBlank() ||
        snapshot.release_sha256 != "bdca54917705cde75fc2d1b275c49a91ebfef05f56f45472d29e4b97ba77c2e9" ||
        snapshot.release_stage != "candidate" || runCatching { OffsetDateTime.parse(snapshot.computed_at) }.isFailure ||
        snapshot.domains?.map { it.domain } != listOf("diabetes", "hypertension")) return null
    if (snapshot.composite_score != null && (!snapshot.composite_score.isFinite() || snapshot.composite_score !in 0.0..100.0)) return null
    if (snapshot.domains.orEmpty().any { !validPersonalDomain(it) }) return null
    if (snapshot.domains.orEmpty().map { it.reference_group }.distinct().size != 1) return null
    return snapshot
}

private fun validPersonalDomain(value: PersonalXaiDomain): Boolean {
    val base = value.base_value ?: return false
    val output = value.output_value ?: return false
    val contributions = value.contributions ?: return false
    return (value.rank ?: 0) in 1..100 && !value.reference_group.isNullOrBlank() && (value.reference_n ?: 0) > 0 &&
        value.output_target == "calibrated_reference_score_before_peer_percentile" && value.unit == "internal_reference_score_point" &&
        value.feature_order == personalFeatureOrder && contributions.map { it.key } == personalFeatureOrder &&
        value.explainer == "ExactExplainer" && value.masker == "Independent" && value.link == "identity" && value.shap_version == "0.49.1" &&
        value.background_rows == 300 && value.background_sha256 == "62407693c4570a4e0bd15a6a8122cdcad59c4a180a127996b7520bcc97df0fb9" &&
        base.isFinite() && output.isFinite() && base in 0.0..100.0 && output in 0.0..100.0 &&
        contributions.all { it.value?.isFinite() == true } && abs(base + contributions.sumOf { it.value!! } - output) <= 1e-9
}

@Composable
internal fun JournalPersonalExplanation(snapshot: PersonalXaiSnapshot, domain: PersonalXaiDomain, showActivity: Boolean = true, onGoMission: (() -> Unit)? = null, practice: JournalPracticeContext? = null) {
    val colors = LocalTmtnColors.current
    var details by remember(snapshot.snapshot_id, domain.domain) { mutableStateOf(false) }
    var expanded by remember(snapshot.snapshot_id, domain.domain) { mutableStateOf(false) }
    val narrative = verifiedPersonalNarrative(domain)
    Column(Modifier.fillMaxWidth().testTag("personal-xai-${domain.domain}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showActivity) verifiedActivityComparison(snapshot)?.let { JournalActivityComparison(it, practice, onGoMission) }
        HorizontalDivider()
        if (practice == null) {
            Text("오늘의 실천을 응원해요", style = TmtnType.caption, color = colors.onSurfaceVariant)
            Text(narrative?.title ?: "실천은 내 속도로 이어가요", style = TmtnType.title, color = colors.onSurface, modifier = Modifier.testTag("personal-narrative-title").semantics { heading() })
            Text(narrative?.summary ?: "오늘의 컨디션에 맞는 실천을 떠올려봐요.", style = TmtnType.body, color = colors.onSurface)
        }
        if (onGoMission != null && practice == null) {
            TextButton(onClick = onGoMission, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("personal-go-mission")) {
                Text("오늘의 미션 보러 가기", style = TmtnType.label)
            }
        }
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("personal-shap-toggle").semantics { stateDescription = if (expanded) "펼쳐짐" else "접힘" }) {
            Text(if (expanded) "계산 과정 접기" else "내 정보가 계산에 어떻게 쓰였을까요?", style = TmtnType.label)
        }
        if (expanded) {
        if (narrative != null) {
            Text(narrative.context_note.orEmpty(), style = TmtnType.body, color = colors.onSurfaceVariant)
            Text(narrative.calculation_title.orEmpty(), style = TmtnType.label, color = colors.onSurface)
            Text(narrative.calculation_summary.orEmpty(), style = TmtnType.body, color = colors.onSurface)
            Text("운동 설문이 반영된 모습", style = TmtnType.label, color = colors.onSurface)
            Text(narrative.activity_text.orEmpty(), style = TmtnType.body, color = colors.onSurface)
            Text("두 지표를 나란히 보면", style = TmtnType.label, color = colors.onSurface)
            Text(narrative.comparison_text.orEmpty(), style = TmtnType.body, color = colors.onSurface)
        }
        Text("내 정보가 반영된 방향", style = TmtnType.title, color = colors.onSurface)
        Text("기준 자료와 비교해, 각 정보가 이번 참고점수에 반영된 방향을 보여드려요.", style = TmtnType.body, color = colors.onSurface)
        val rows = domain.contributions.orEmpty()
        val maximum = rows.maxOfOrNull { abs(it.value ?: 0.0) }?.coerceAtLeast(personalDisplayEpsilon) ?: 1.0
        rows.forEachIndexed { index, item ->
            val value = item.value ?: 0.0
            val direction = personalDirectionLabel(value)
            Column(Modifier.clearAndSetSemantics { contentDescription = "${personalLabels[index]}, $direction" }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(personalLabels[index], style = TmtnType.label, color = colors.onSurface)
                    Text(direction, style = TmtnType.caption, color = colors.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(5.dp), contentAlignment = Alignment.CenterEnd) {
                        if (value < -personalDisplayEpsilon) Box(Modifier.fillMaxWidth((abs(value) / maximum).toFloat()).fillMaxHeight().background(Color(0xFF956549)))
                    }
                    Box(Modifier.width(1.dp).height(13.dp).background(colors.onSurfaceVariant))
                    Box(Modifier.weight(1f).height(5.dp)) {
                        if (value > personalDisplayEpsilon) Box(Modifier.fillMaxWidth((abs(value) / maximum).toFloat()).fillMaxHeight().background(Color(0xFF3F5D4B)))
                    }
                }
            }
        }
        Text(narrative?.scope_note ?: "모델의 계산을 풀어 본 설명이에요. 실제 건강이 달라졌다는 뜻은 아니에요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
        TextButton(onClick = { details = true }, modifier = Modifier.testTag("personal-source-${domain.domain}")) { Text("계산 근거 자세히 보기", style = TmtnType.label) }
        }
    }
    if (details) AlertDialog(
        onDismissRequest = { details = false }, title = { Text("내 결과와 연결된 설명", style = TmtnType.title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("점수와 설명은 같은 입력값·같은 모델로 함께 계산했어요.", style = TmtnType.body)
            Text("SHAP는 모델이 입력을 어떻게 반영했는지 나눠 보는 방법이에요. 기준값에 모든 입력의 기여도를 더하면 이번 내부 참고점수와 같아져요.", style = TmtnType.body)
            Text("설명의 기준은 개발 배경 표본 300건이에요. 등수를 구할 때 쓰는 같은 성별·연령대 비교집단과는 달라요.", style = TmtnType.body)
            Text(snapshot.age_notice.orEmpty(), style = TmtnType.body)
            Text("모델 · ${snapshot.model_version}\n설명 방법 · Exact SHAP ${domain.shap_version}\n입력 기준일 · ${snapshot.reference_date}\n결과 번호 · ${snapshot.snapshot_id?.take(8)}", style = TmtnType.caption)
            Text("검토 중인 모델의 참고 결과예요. 신체·생활습관·허리둘레에는 이 질환 모델의 설명을 붙이지 않아요.", style = TmtnType.caption)
        } },
        confirmButton = { TextButton(onClick = { details = false }) { Text("확인") } },
    )
}

internal fun verifiedPersonalNarrative(domain: PersonalXaiDomain): PersonalShapNarrative? {
    val story = domain.narrative ?: return null
    if (story.version != "tmtn-shap-story-v2" || story.domain != domain.domain || story.intro_kind != "challenge_encouragement" ||
        listOf(story.title, story.summary, story.calculation_title, story.calculation_summary, story.context_note, story.activity_text, story.comparison_text, story.scope_note).any { it.isNullOrBlank() } ||
        story.comparison_kind !in setOf("different_direction", "different_leader", "different_next_tier", "shared_pattern") ||
        listOf(story.focus_keys, story.activity_keys, story.comparison_keys).any { keys -> keys == null || keys.any { it !in personalFeatureOrder } } ||
        story.activity_keys != personalFeatureOrder.takeLast(2)) return null
    val rows = domain.contributions.orEmpty()
    if (rows.size != 6 || rows.any { it.value?.isFinite() != true }) return null
    val maximum = rows.maxOf { abs(it.value!!) }
    val expected = rows.filter { maximum > personalDisplayEpsilon && abs(abs(it.value!!) - maximum) <= personalDisplayEpsilon }.map { it.key }.toSet()
    if (story.focus_keys?.toSet() != expected) return null
    return story
}

@Composable
internal fun JournalPersonalStatus(state: JournalLoad<PersonalXaiResponse>?, onRetry: () -> Unit, onEdit: () -> Unit, practice: JournalPracticeContext? = null, onGoMission: (() -> Unit)? = null) {
    val colors = LocalTmtnColors.current
    val response = (state as? JournalLoad.Ready)?.value
    val reason = response?.reason
    if (reason !in listOf("consent_required", "input_required", "input_invalid", "strength_days_unconfirmed", "release_review_required", "not_configured")) {
        verifiedActivityData(response?.activity_comparison)?.let { JournalActivityComparison(it, practice, onGoMission) }
    }
    when {
        state == null -> Unit
        response?.status == "pending" -> Text("활동 비교를 읽는 동안, 모델의 계산 설명을 준비하고 있어요.", style = TmtnType.caption, color = colors.onSurfaceVariant)
        state == JournalLoad.Loading -> Text("입력한 정보로 개인별 설명을 계산하고 있어요. 다른 기사는 먼저 읽을 수 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
        reason in listOf("strength_days_unconfirmed", "input_required", "input_invalid") -> Column {
            Text(if (reason == "strength_days_unconfirmed") "근력운동을 한 일수를 확인하면 개인별 설명을 준비할 수 있어요." else "개인별 설명에 필요한 몸과 운동 정보를 확인해 주세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            TextButton(onClick = onEdit) { Text("입력 정보 확인하기", style = TmtnType.label) }
        }
        reason == "consent_required" -> Text("내 정보에서 건강 참고 분석 동의를 확인해 주세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
        reason in listOf("not_configured", "release_review_required") -> Text("개인별 설명을 준비하고 있어요. 지금은 실천 기록을 살펴볼 수 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
        reason in listOf("busy", "calculation_failed") -> Column {
            Text(if (reason == "busy") "계산 요청이 잠시 몰렸어요. 조금 뒤 다시 불러와 주세요." else "계산 설명을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            TextButton(onClick = onRetry) { Text("계산 설명 다시 불러오기", style = TmtnType.label) }
        }
        verifiedPersonalSnapshot(state) == null -> Column {
            Text("지금은 개인별 설명을 보여드리기 어려워요. 입력 정보를 확인하거나 다시 불러올 수 있어요.", style = TmtnType.body, color = colors.onSurfaceVariant)
            TextButton(onClick = onRetry) { Text("개인별 설명 다시 불러오기", style = TmtnType.label) }
        }
    }
}
