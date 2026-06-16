package com.app.dingdong.model

import com.google.gson.annotations.SerializedName

// 🌟 라즈베리파이에서 보낸 JSON의 키값("message", "bg_color")을 안드로이드가 인식하도록 매핑하는 모델 클래스입니다.
data class DisplayStatusResponse(
    @SerializedName("message") val message: String,
    @SerializedName("bg_color") val bg_color: String
)