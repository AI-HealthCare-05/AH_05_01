package com.tmtn.app.ui.common

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class RequestFeedbackTest {
    @Test fun networkAndParserErrorsDoNotExposeTechnicalMessages() {
        assertEquals("연결을 확인한 뒤 다시 시도해 주세요.", UnknownHostException("private-host.example").userMessageOr("다시 시도해 주세요."))
        assertEquals("기록을 불러오지 못했어요.", IllegalStateException("Expected BEGIN_OBJECT at line 1").userMessageOr("기록을 불러오지 못했어요."))
        assertTrue(SocketTimeoutException("timeout").userMessageOr("실패").contains("잠시 후"))
    }

    @Test fun deliberateGuidanceIsPreservedAndCancellationIsNotAnError() {
        val failure = runCatching { failWithMessage("비밀번호가 서로 달라요.") }.exceptionOrNull()!!
        assertEquals("비밀번호가 서로 달라요.", failure.userMessageOr("실패"))
        val cancellation = CancellationException("left screen")
        assertSame(cancellation, runCatching { cancellation.userMessageOr("실패") }.exceptionOrNull())
    }

    @Test fun fieldValidationNamesTheInputWithoutRepeatingItsValue() {
        val body = """{"detail":[{"loc":["body","email"],"msg":"value is not a valid email address","input":"private-value"},{"loc":["body","email"],"msg":"invalid"}]}"""
        assertEquals("이메일 항목을 다시 확인해 주세요.", readableHttpError(422, body))
        assertEquals("비밀번호는 8자 이상이어야 해요.", readableHttpError(422, """{"detail":[{"msg":"Value error, 비밀번호는 8자 이상이어야 해요."}]}"""))
    }

    @Test fun serverFailuresAndMalformedBodiesStayReadable() {
        val value = readableHttpError(500, """{"detail":"오류: database password=hidden"}""")
        assertFalse(value.contains("hidden"))
        assertTrue(value.contains("잠시 후"))
        assertFalse(readableHttpError(409, "<html>upstream error</html>").contains("html"))
        assertTrue(readableHttpError(429, null).contains("조금 뒤"))
    }

    @Test fun knownConsentAndLoginMessagesKeepTheirNextAction() {
        assertEquals("이메일 또는 비밀번호를 확인해 주세요.", readableHttpError(401, """{"detail":"이메일 또는 비밀번호를 확인해 주세요."}"""))
        assertTrue(readableHttpError(403, """{"detail":"HEALTH_REFERENCE_ANALYSIS_NOT_AGREED"}""").contains("분석 동의"))
    }
}
