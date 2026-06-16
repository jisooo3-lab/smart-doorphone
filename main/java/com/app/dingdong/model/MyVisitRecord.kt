package com.app.dingdong.model

// 🌟 충돌 방지를 위해 이름을 MyVisitRecord로 차별화한 청정 데이터 상자입니다.
data class MyVisitRecord(
    val id: Int,
    val timestamp: String,
    val image_name: String,
    val label: String
)