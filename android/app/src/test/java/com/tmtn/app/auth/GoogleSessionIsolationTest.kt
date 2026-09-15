package com.tmtn.app.auth

import com.tmtn.app.network.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class GoogleSessionIsolationTest {
    @After fun resetMemory() { TokenHolder.clear(); SessionManager.clear() }

    @Test fun expiredGoogleProofDoesNotAttachAnOldAppTokenOrExpireTheAppSession() {
        TokenHolder.accessToken = "synthetic-app-token"
        SessionManager.clear()
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor()).addInterceptor(SessionInterceptor())
            .addInterceptor { chain ->
                assertNull(chain.request().header("Authorization"))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(401)
                    .message("Unauthorized").body("".toResponseBody()).build()
            }.build()
        client.newCall(Request.Builder().url("https://example.test/api/v1/auth/google").build()).execute().use {
            assertEquals(401, it.code)
            assertEquals("synthetic-app-token", TokenHolder.accessToken)
            assertFalse(SessionManager.sessionExpired.value)
        }
    }

    @Test fun protectedApiStillExpiresARejectedAppSession() {
        TokenHolder.accessToken = "synthetic-app-token"
        SessionManager.clear()
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor()).addInterceptor(SessionInterceptor())
            .addInterceptor { chain ->
                assertEquals("Bearer synthetic-app-token", chain.request().header("Authorization"))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(401)
                    .message("Unauthorized").body("".toResponseBody()).build()
            }.build()
        client.newCall(Request.Builder().url("https://example.test/api/v1/users/me").build()).execute().use {
            assertNull(TokenHolder.accessToken)
            assertTrue(SessionManager.sessionExpired.value)
        }
    }
}
