package com.tmtn.app.ui.common

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException

private class UiMessageException(message: String) : IllegalStateException(message)

/** Marks copy that the app deliberately prepared for the person using this screen. */
internal fun failWithMessage(message: String): Nothing = throw UiMessageException(message)

internal fun Throwable.userMessageOr(fallback: String): String = when (this) {
    is CancellationException -> throw this
    is UiMessageException -> message ?: fallback
    is SocketTimeoutException -> "응답을 기다리는 시간이 길어졌어요. 잠시 후 다시 시도해 주세요."
    is IOException -> "연결을 확인한 뒤 다시 시도해 주세요."
    else -> fallback
}

/** Keeps field guidance useful without printing English parser errors, URLs, or server internals. */
internal fun readableHttpError(code: Int, body: String?): String {
    val fallback = when (code) {
        401 -> "로그인이 필요해요. 다시 로그인해 주세요."
        403 -> "이 작업을 진행할 수 없어요. 필요한 동의와 권한을 확인해 주세요."
        404 -> "찾으려던 내용을 확인하지 못했어요. 다시 불러와 주세요."
        408, 504 -> "응답을 기다리는 시간이 길어졌어요. 잠시 후 다시 시도해 주세요."
        409 -> "내용이 변경됐어요. 최신 내용을 불러온 뒤 다시 시도해 주세요."
        429 -> "요청이 잠시 몰렸어요. 조금 뒤에 다시 시도해 주세요."
        in 400..499 -> "입력한 내용을 다시 확인해 주세요."
        else -> "서버의 응답을 확인하지 못했어요. 잠시 후 다시 시도해 주세요."
    }
    if (code >= 500 || body.isNullOrBlank()) return fallback
    val detail = runCatching { JsonParser.parseString(body).asJsonObject.get("detail") }.getOrNull() ?: return fallback
    return when {
        detail.isJsonPrimitive -> {
            val message = runCatching { detail.asString }.getOrNull()
            when (message) {
                "HEALTH_REFERENCE_ANALYSIS_NOT_AGREED" -> "틈튼지수를 보려면 건강정보 분석 동의를 확인해 주세요."
                "LOCATION_DATA_USAGE_NOT_AGREED" -> "거리 측정에 필요한 위치정보 동의를 확인해 주세요."
                else -> readableKorean(message) ?: fallback
            }
        }
        detail.isJsonArray -> detail.asJsonArray.mapNotNull(::fieldGuidance).distinct().take(3).joinToString("\n").ifBlank { fallback }
        else -> fallback
    }
}

private fun readableKorean(raw: String?): String? {
    val text = raw?.removePrefix("Value error, ")?.trim() ?: return null
    if (text.length !in 2..220 || !text.any { it in '가'..'힣' }) return null
    if (Regex("https?://|traceback|exception|<|>|\\b(?:password|token|secret)\\s*[:=]", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
    return text
}

private fun fieldGuidance(item: JsonElement): String? = runCatching {
    val field = item.asJsonObject
    readableKorean(field.get("msg")?.asString)?.let { return it }
    val key = field.getAsJsonArray("loc")?.lastOrNull()?.asString
    val label = when (key) {
        "email", "new_email" -> "이메일"
        "password", "new_password", "current_password" -> "비밀번호"
        "name" -> "이름"
        "nickname" -> "별명"
        "birth_year", "birth_month" -> "태어난 연·월"
        "gender", "sex" -> "성별"
        "is_pregnant" -> "임신 여부"
        "height_cm" -> "키"
        "weight_kg" -> "몸무게"
        "code", "verification_code" -> "인증번호"
        "strength_weekly_count" -> "근력 운동 일수"
        "strength_intensity" -> "근력 운동 강도"
        "aerobic_low_minutes", "aerobic_moderate_minutes", "aerobic_high_minutes" -> "유산소 운동 시간"
        "memo" -> "메모"
        else -> return null
    }
    "$label 항목을 다시 확인해 주세요."
}.getOrNull()
