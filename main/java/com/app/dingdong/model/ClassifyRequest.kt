package com.app.dingdong.model

// 부모가 앱에서 방문자를 분류할 때 서버로 보낼 데이터 구조입니다.
data class ClassifyRequest(
    val type: String
)

// 서버에서 응답(성공 여부)을 보낼 때 받을 데이터 구조입니다. [cite: 437]
data class StatusResponse(
    val status: String
)

// 나중에 '방문 기록' 화면을 구현할 때 사용할 데이터 구조입니다. [cite: 439]
data class VisitRecord(
    val time: String,
    val result: String
)