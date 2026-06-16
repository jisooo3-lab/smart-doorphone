package com.app.dingdong

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// 1. 주고받을 데이터 모양 상자 (SDP와 Type)
data class SdpMessage(
    val sdp: String,
    val type: String
)

// 2. 서버 통신 규칙 명세서
interface WebRtcApi {

    // 라즈베리파이의 /offer 주소로 데이터를 보냅니다.
    @POST("/offer")
    fun sendOffer(@Body offer: SdpMessage): Call<SdpMessage>

    companion object {
        // ⭐ 중요: 여기에 아까 확인하신 라즈베리파이의 실제 IP 주소를 적어주세요!
        private const val BASE_URL = "http://172.20.10.9:5001"

        fun create(): WebRtcApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WebRtcApi::class.java)
        }
    }
}