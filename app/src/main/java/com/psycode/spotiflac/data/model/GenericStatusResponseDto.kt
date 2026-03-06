package com.psycode.spotiflac.data.model

import com.google.gson.annotations.SerializedName

data class GenericStatusResponseDto(
    @SerializedName("status") val status: String
)