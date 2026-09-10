package com.tmtn.app.network.model

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/** Read-only projection of the existing /prediction-results/latest response. */
data class PredictionResultResponse(
    @SerializedName("submodel_type") val submodelType: String? = null,
    val value: BigDecimal? = null,
    val status: String? = null,
    @SerializedName("computed_at") val computedAt: String? = null,
    @SerializedName("model_version") val modelVersion: String? = null,
)
