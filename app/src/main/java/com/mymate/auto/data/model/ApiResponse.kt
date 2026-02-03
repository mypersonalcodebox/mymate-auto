package com.mymate.auto.data.model

data class ApiRequest(
    val message: String,
    val source: String = "android_auto",
    val quickActionId: String? = null
)

data class ApiResponse(
    val reply: String?,
    val error: String?,
    val tts: Boolean? = true
)
