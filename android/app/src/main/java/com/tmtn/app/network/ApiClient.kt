package com.tmtn.app.network

import com.tmtn.app.network.model.LoginRequest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    // TODO: 팀원들과 같은 Wi-Fi에서 테스트할 때는 이 주소를 바꿔야 함
    private const val BASE_URL = "https://last-broiling-tartly.ngrok-free.dev/api/v1/"

    // ⚠️ A01~A10 온보딩 화면이 생겨서 이제 여기서만 임시로 쓰이는 용도는 아니지만,
    // 혹시 온보딩 건너뛰고 바로 기능 테스트하고 싶을 때 쓸 수 있게 남겨둠.
    private const val TEST_EMAIL = "test@tmtn.com"
    private const val TEST_PASSWORD = "Test1234!"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
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

    /**
     * 실제 온보딩 화면(A03~A10)이 생겨서, 이제 이 함수는 "온보딩 건너뛰고 바로 기능 테스트"
     * 하고 싶을 때 쓰는 용도로만 남겨둠. 최초 온보딩 흐름 자체는 OnboardingState가 담당.
     */
    suspend fun loginWithTestAccount(): Boolean {
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
