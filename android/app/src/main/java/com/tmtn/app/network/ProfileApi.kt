package com.tmtn.app.network

import com.tmtn.app.network.model.AccessibilityResponse
import com.tmtn.app.network.model.AccessibilityUpdateRequest
import com.tmtn.app.network.model.AccountDeleteRequest
import com.tmtn.app.network.model.ConsentRequest
import com.tmtn.app.network.model.ConsentResponse
import com.tmtn.app.network.model.EmailChangeRequest
import com.tmtn.app.network.model.ExerciseHabitsRequest
import com.tmtn.app.network.model.ExerciseHabitsResponse
import com.tmtn.app.network.model.HealthInputRequest
import com.tmtn.app.network.model.HealthInputResponse
import com.tmtn.app.network.model.NotificationSettingResponse
import com.tmtn.app.network.model.NotificationSettingUpdateRequest
import com.tmtn.app.network.model.PasswordChangeRequest
import com.tmtn.app.network.model.UserInfoResponse
import com.tmtn.app.network.model.UserUpdateRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

interface ProfileApi {

    @GET("users/me")
    suspend fun getMe(): Response<UserInfoResponse>

    @PATCH("users/me")
    suspend fun updateMe(@Body body: UserUpdateRequest): Response<UserInfoResponse>

    @GET("health-inputs/latest")
    suspend fun getLatestHealthInput(): Response<HealthInputResponse>

    @POST("health-inputs")
    suspend fun submitHealthInput(@Body body: HealthInputRequest): Response<HealthInputResponse>

    @GET("exercise-habits/latest")
    suspend fun getLatestExerciseHabits(): Response<ExerciseHabitsResponse>

    @POST("exercise-habits")
    suspend fun submitExerciseHabits(@Body body: ExerciseHabitsRequest): Response<ExerciseHabitsResponse>

    @GET("consents")
    suspend fun listConsents(): Response<List<ConsentResponse>>

    @POST("consents")
    suspend fun agreeConsent(@Body body: ConsentRequest): Response<ConsentResponse>

    @DELETE("consents/{purpose}")
    suspend fun withdrawConsent(@Path("purpose") purpose: String): Response<ConsentResponse>

    @GET("accessibility")
    suspend fun getAccessibility(): Response<AccessibilityResponse>

    @PATCH("accessibility")
    suspend fun updateAccessibility(@Body body: AccessibilityUpdateRequest): Response<AccessibilityResponse>

    @GET("notification-settings")
    suspend fun getNotificationSettings(): Response<NotificationSettingResponse>

    @PATCH("notification-settings")
    suspend fun updateNotificationSettings(@Body body: NotificationSettingUpdateRequest): Response<NotificationSettingResponse>

    @PATCH("users/me/password")
    suspend fun changePassword(@Body body: PasswordChangeRequest): Response<Unit>

    @PATCH("users/me/email")
    suspend fun changeEmail(@Body body: EmailChangeRequest): Response<UserInfoResponse>

    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun deleteAccount(@Body body: AccountDeleteRequest): Response<Unit>

    @DELETE("users/me/records")
    suspend fun deleteRecordsOnly(): Response<Unit>

    @Streaming
    @GET("users/me/export")
    suspend fun exportMyData(): Response<okhttp3.ResponseBody>

    @POST("inquiries")
    suspend fun createInquiry(
        @Body body: com.tmtn.app.network.model.InquiryCreateRequest
    ): Response<com.tmtn.app.network.model.InquiryResponse>
}
