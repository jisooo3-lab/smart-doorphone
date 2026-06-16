package com.app.dingdong.api

import com.app.dingdong.model.ClassifyRequest
import com.app.dingdong.model.MyVisitRecord
import com.app.dingdong.model.DisplayStatusResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface DoorApi {
    @GET("/history")
    fun getHistory(): Call<List<MyVisitRecord>>

    @GET("/alert")
    fun checkAlert(): Call<ResponseBody>

    @GET("/visitor_image")
    fun getVisitorImage(): Call<ResponseBody>

    @GET("/latest_image")
    fun getCaptureNow(): Call<ResponseBody>

    @POST("/classify")
    fun classifyVisitor(@Body request: ClassifyRequest): Call<ResponseBody>

    // 🌟 [음성 추가 1]: 내 목소리 파일을 파이로 업로드하는 통로
    @Multipart
    @POST("/upload_voice")
    fun uploadVoice(@Part file: MultipartBody.Part): Call<ResponseBody>

    // 🌟 [음성 추가 2]: 파이가 녹음한 현관 소리를 다운로드하는 통로
    @GET("/listen_voice")
    fun listenVoice(): Call<ResponseBody>

    @GET("display_status")
    fun getDisplayStatus(): Call<DisplayStatusResponse>
}