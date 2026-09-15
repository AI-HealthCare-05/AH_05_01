package com.tmtn.app.auth

import com.google.gson.JsonParser
import com.tmtn.app.network.model.SocialLoginResponse
import com.tmtn.app.ui.common.failWithMessage
import com.tmtn.app.ui.common.readableHttpError
import retrofit2.Response

internal sealed interface GoogleLoginOutcome {
    data class SignedIn(val session: SocialLoginResponse) : GoogleLoginOutcome
    data class ConsentRequired(val email: String) : GoogleLoginOutcome
    data class LinkRequired(val email: String) : GoogleLoginOutcome
}

internal fun googleLoginOutcome(response: Response<SocialLoginResponse>): GoogleLoginOutcome {
    if (response.isSuccessful) {
        val session = response.body()?.takeIf { it.access_token.isNotBlank() }
            ?: failWithMessage("로그인 정보를 확인하지 못했어요. 다시 시도해 주세요.")
        return GoogleLoginOutcome.SignedIn(session)
    }
    val raw = response.errorBody()?.string()
    if (response.code() == 401) failWithMessage("Google 인증이 만료됐어요. 계정을 다시 선택해 주세요.")
    if (response.code() == 409) {
        val body = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
        val code = runCatching { body?.get("code")?.asString }.getOrNull()
        val email = runCatching { body?.get("email")?.asString }.getOrNull().orEmpty()
        when (code) {
            "SIGNUP_REQUIRED" -> return GoogleLoginOutcome.ConsentRequired(email)
            "LINK_REQUIRED" -> return GoogleLoginOutcome.LinkRequired(email)
        }
    }
    failWithMessage(readableHttpError(response.code(), raw))
}
