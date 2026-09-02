package com.tmtn.app.network

import com.tmtn.app.network.model.ConsentRequest
import com.tmtn.app.network.model.ConsentResponse
import com.tmtn.app.network.model.EmailVerificationConfirmRequest
import com.tmtn.app.network.model.EmailVerificationRequestRequest
import com.tmtn.app.network.model.ExerciseHabitsRequest
import com.tmtn.app.network.model.ExerciseHabitsResponse
import com.tmtn.app.network.model.HealthInputRequest
import com.tmtn.app.network.model.HealthInputResponse
import com.tmtn.app.network.model.LoginResponse
import com.tmtn.app.network.model.NotificationSettingResponse
import com.tmtn.app.network.model.OnboardingScheduleRequest
import com.tmtn.app.network.model.UserInfoResponse
import com.tmtn.app.network.model.UserUpdateRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST

interface OnboardingApi {

    // A03
    @POST("auth/email-verification/request")
    suspend fun requestEmailVerification(@Body body: EmailVerificationRequestRequest): Response<Map<String, Any>>

    // A04 - 성공하면 로그인 응답(access_token)과 동일한 형태로 옴 (자동 로그인)
    @POST("auth/email-verification/confirm")
    suspend fun confirmEmailVerification(@Body body: EmailVerificationConfirmRequest): Response<LoginResponse>

    // A06
    @POST("consents")
    suspend fun agreeConsent(@Body body: ConsentRequest): Response<ConsentResponse>

    // A07 - 이름/성별/생년월 등
    @PATCH("users/me")
    suspend fun updateProfile(@Body body: UserUpdateRequest): Response<UserInfoResponse>

    // A07 - 키/몸무게 (별도 API, health_input_snapshots)
    @POST("health-inputs")
    suspend fun submitHealthInput(@Body body: HealthInputRequest): Response<HealthInputResponse>

    // A08
    @POST("exercise-habits")
    suspend fun submitExerciseHabits(@Body body: ExerciseHabitsRequest): Response<ExerciseHabitsResponse>

    // A09~A10
    @POST("notification-settings/schedule")
    suspend fun submitSchedule(@Body body: OnboardingScheduleRequest): Response<NotificationSettingResponse>
}
