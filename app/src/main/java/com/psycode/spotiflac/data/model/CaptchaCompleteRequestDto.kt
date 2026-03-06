package com.psycode.spotiflac.data.model

import com.google.gson.annotations.SerializedName

data class CaptchaCompleteRequestDto(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("solution")   val solution: String
)