package com.psycode.spotiflac.data.model

import com.google.gson.annotations.SerializedName

data class ErrorResponse(
    @SerializedName("detail")
    val detail: CaptchaInitResponseDto
)