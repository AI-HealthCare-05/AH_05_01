package com.tmtn.app.network

import com.tmtn.app.BuildConfig
import com.tmtn.app.network.model.LoginRequest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.tmtn.app.network.TuntunScoreApi

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
        // ⚠️ 2026-09-02 1차 반영: release에서 BODY가 그대로 찍히던 건 막았음.
        // 2026-09-03 리뷰 반영: DEBUG에서도 BODY는 위험함 — 로그인 요청 본문에 팀원
        // 계정의 평문 비밀번호가 그대로 남음. DEBUG는 BASIC(메서드·URL·응답코드·시간만),
        // RELEASE는 NONE으로 낮춤.
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    private val authInterceptor = AuthInterceptor()
    private val sessionInterceptor = SessionInterceptor()

    // ⚠️ 2026-09-03 리뷰 반영: CookieJar가 없어서 로그인 응답의 refresh_token 쿠키가
    // 저장조차 안 되고 있었음(리프레시 흐름 자체가 불가능한 상태였음).
    private val cookieJar = InMemoryCookieJar()

    // refresh 호출 전용 - authenticator를 안 달아서 재시도가 재시도를 부르는 루프가 안 생김.
    // cookieJar는 메인 클라이언트와 공유해서 같은 refresh_token을 씀.
    private val refreshOkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(loggingInterceptor)
        .build()

    private val tokenAuthenticator = TokenAuthenticator(BASE_URL, refreshOkHttpClient)

    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(authInterceptor)
        .addInterceptor(sessionInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
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
        } catch (e: java.io.IOException) {
            // ⚠️ 2026-09-03 리뷰 반영: catch (e: Exception)로 다 삼키면 네트워크 실패와
            // 인증 실패가 구분이 안 됨. 여기선 온보딩 건너뛰기용 편의 함수라 어차피 Boolean만
            // 돌려주지만, 최소한 "네트워크 자체가 안 됐다"는 걸 로그로는 구분해서 남김.
            android.util.Log.w("ApiClient", "테스트 계정 로그인 실패 - 네트워크 오류", e)
            false
        }
    }
}
