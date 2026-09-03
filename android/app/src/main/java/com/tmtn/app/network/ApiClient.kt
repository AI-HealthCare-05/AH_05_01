package com.tmtn.app.network

import com.tmtn.app.BuildConfig
import com.tmtn.app.network.model.LoginRequest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    // TODO: 팀원들과 같은 Wi-Fi에서 테스트할 때는 이 주소를 바꿔야 함
    private const val BASE_URL = "https://last-broiling-tartly.ngrok-free.dev/api/v1/"

    // ⚠️ 2026-09-02 리뷰 반영: 예전엔 이메일·비밀번호가 여기 평문으로 박혀 있어서 git
    // 히스토리에 그대로 남았음(서버 쪽 실제 비밀번호는 별도로 교체 필요). 이제 local.properties
    // (.gitignore에 이미 있어서 커밋 안 됨)에서 build.gradle.kts가 읽어 BuildConfig로 주입.
    // local.properties에 값이 없으면 빈 문자열이고, loginWithTestAccount()가 그때는 그냥
    // 실패 처리함(필수 기능 아님 - 온보딩 건너뛰고 테스트하고 싶을 때만 쓰는 편의 기능).
    private val TEST_EMAIL get() = BuildConfig.TEST_ACCOUNT_EMAIL
    private val TEST_PASSWORD get() = BuildConfig.TEST_ACCOUNT_PASSWORD

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // ⚠️ 2026-09-02 리뷰 반영: BODY 레벨이 release 빌드에도 그대로 적용돼서, 토큰·
        // 비밀번호·건강정보가 로그캣에 전부 찍히고 있었음. 디버그 빌드에서만 BODY로 보이고,
        // release에서는 아예 로깅하지 않도록 분기.
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private val authInterceptor = AuthInterceptor()
    private val sessionInterceptor = SessionInterceptor()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(sessionInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val missionApi: MissionApi by lazy { retrofit.create(MissionApi::class.java) }
    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    val onboardingApi: OnboardingApi by lazy { retrofit.create(OnboardingApi::class.java) }
    val cardHomeApi: CardHomeApi by lazy { retrofit.create(CardHomeApi::class.java) }
    val recordApi: RecordApi by lazy { retrofit.create(RecordApi::class.java) }
    val profileApi: ProfileApi by lazy { retrofit.create(ProfileApi::class.java) }
    val tuntunScoreApi: TuntunScoreApi by lazy { retrofit.create(TuntunScoreApi::class.java) }

    /**
     * 실제 온보딩 화면(A03~A10)이 생겨서, 이제 이 함수는 "온보딩 건너뛰고 바로 기능 테스트"
     * 하고 싶을 때 쓰는 용도로만 남겨둠. 최초 온보딩 흐름 자체는 OnboardingState가 담당.
     */
    suspend fun loginWithTestAccount(): Boolean {
        if (!BuildConfig.DEBUG || TEST_PASSWORD.isBlank()) return false
        return try {
            val response = authApi.login(LoginRequest(email = TEST_EMAIL, password = TEST_PASSWORD))
            if (response.isSuccessful) {
                TokenHolder.accessToken = response.body()?.access_token
                TokenHolder.accessToken != null
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
