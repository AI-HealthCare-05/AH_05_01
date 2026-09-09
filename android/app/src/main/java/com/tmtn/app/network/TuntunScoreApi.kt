package com.tmtn.app.network

import com.tmtn.app.network.model.ScoreInputsResponse
import com.tmtn.app.network.model.TmtnScoreResponse
import com.tmtn.app.network.model.TuntunScoreOrEligibilityResponse
import com.tmtn.app.network.model.TuntunScorePeerV2Response
import com.tmtn.app.network.model.TuntunScoreV2Response
import retrofit2.Response
import retrofit2.http.GET

interface TuntunScoreApi {

    @GET("tuntun-score")
    suspend fun getTuntunScore(): Response<TuntunScoreOrEligibilityResponse>

    @GET("tuntun-score/v2")
    suspend fun getTuntunScoreV2(): Response<TuntunScoreV2Response>

    // ⚠️ 2026-09-09 추가 - 실모델(또래 백분위) 연동. 위 getTuntunScoreV2()는 이제 이 화면에서
    // 안 씀(Mock이라 안 씀) - 다만 서버 쪽 기존 계약을 다른 데서 쓸 수도 있어서 그대로 둠.
    @GET("tuntun-score/peer/v2")
    suspend fun getTuntunScorePeerV2(): Response<TuntunScorePeerV2Response>

    @GET("tuntun-score/about")
    suspend fun getTuntunScoreAbout(): Response<TmtnScoreResponse>

    @GET("tuntun-score/inputs")
    suspend fun getTuntunScoreInputs(): Response<ScoreInputsResponse>
}
