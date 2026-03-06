package com.psycode.spotiflac.data.model

import com.google.gson.annotations.SerializedName

data class CaptchaInitResponseDto(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("captcha_image") val captchaImage: String
)