package com.psycode.spotiflac.domain.model

class CaptchaRequiredException(
    val sessionId: String,
    val captchaImageUrl: String
) : Exception("Captcha required")

