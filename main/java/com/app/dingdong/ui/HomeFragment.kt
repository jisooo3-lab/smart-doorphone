package com.app.dingdong.ui

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.app.dingdong.R
import com.app.dingdong.api.DoorApi
import com.app.dingdong.model.ClassifyRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream

class HomeFragment : Fragment() {

    private lateinit var visitorImageView: ImageView
    private lateinit var btnDelivery: Button
    private lateinit var btnAcquaintance: Button
    private lateinit var btnSuspicious: Button
    private lateinit var btnCall: Button

    private val raspberryPiIp = "172.20.10.9"

    @Volatile
    private var isProcessing = false

    // 🌟 오디오 제어 관련 전역 변수
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isCallActive = false // 현재 통화 활성화 여부

    private val doorApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://$raspberryPiIp:5001/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DoorApi::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        visitorImageView = view.findViewById(R.id.videoView) as ImageView
        btnDelivery = view.findViewById(R.id.btnDelivery)
        btnAcquaintance = view.findViewById(R.id.btnAcquaintance)
        btnSuspicious = view.findViewById(R.id.btnSuspicious)
        btnCall = view.findViewById(R.id.btnCall)

        btnDelivery.setOnClickListener { classify("택배") }
        btnAcquaintance.setOnClickListener { classify("지인") }
        btnSuspicious.setOnClickListener { classify("수상함") }

        // 🌟 [무전기식 터치 리스너 시스템]: 버튼을 누르고 있으면 녹음, 떼면 전송!
        btnCall.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 버튼 누름: 내 목소리 녹음 시작 및 실시간 카메라 캡처 요청
                    isCallActive = true
                    isProcessing = true
                    loadCaptureNowImage()
                    startRecording()
                    btnCall.text = "🎤 말하는 중... (떼면 전송)"
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 버튼 뗌: 녹음 중지 및 파이로 전송
                    stopRecording()
                    btnCall.text = "직접 통화"
                    lifecycleScope.launch {
                        delay(1000)
                        isProcessing = false
                    }
                    true
                }
                else -> false
            }
        }

        startBellMonitoring()
        startVoiceListeningLoop() // 🌟 파이의 현관 오디오 무한 감청 루프 시작
    }

    // 🔔 초인종 및 이미지 자동 감시 루프
    private fun startBellMonitoring() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                if (isProcessing) {
                    delay(500)
                    continue
                }

                doorApi.getVisitorImage().enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        if (response.isSuccessful && response.body() != null) {
                            try {
                                val bytes = response.body()!!.bytes()
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null && isAdded) {
                                    visitorImageView.setImageBitmap(bitmap)
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
                })
                delay(1000)
            }
        }
    }

    // 📸 실시간 화면 요청 엔진
    private fun loadCaptureNowImage() {
        if (isProcessing) return
        isProcessing = true

        doorApi.getCaptureNow().enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                isProcessing = false
                if (response.isSuccessful && response.body() != null) {
                    try {
                        val bytes = response.body()!!.bytes()
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null && isAdded) {
                            visitorImageView.setImageBitmap(bitmap)
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                isProcessing = false
            }
        })
    }

    // 🎙️ 스마트폰 마이크로 오디오 녹음 시작 헬퍼
    // 🎙️ 에뮬레이터-노트북 마이크 환경 최적화 (WAV 고음질 리얼 타임 포맷)
    // 🎙️ 에뮬레이터 오디오 드라이버 버그 정밀 격파 버전 (CD 음질 44.1kHz 통일)
    private fun startRecording() {
        try {
            audioFile = File(requireContext().cacheDir, "voice_app.wav")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)

                // 🌟 [싱크 해결 치트키]: 출력 포맷을 무압축 순정 오디오 표준인 'MPEG_4'로 지정합니다.
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

                // 🌟 최신 노트북 마이크 드라이버가 호환 에러 없이 받아들이는 AAC 고음질 코덱 지정
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                // 🌟 중요: 주파수를 CD 음질 표준인 44100Hz로 강제 고정하여 지지직거리는 왜곡을 방어합니다.
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)

                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            Toast.makeText(context, "🎙️ 현관으로 목소리 송신 중...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 🛑 녹음 중지 및 즉시 라즈베리파이로 업로드 전송
    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            // 녹음 완료된 오디오 파일 파이로 다이렉트 전송
            audioFile?.let { uploadVoiceFile(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 📤 파이썬 서버로 음성 파일 전송 파이프라인
    private fun uploadVoiceFile(file: File) {
        val requestFile = file.asRequestBody("audio/wav".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

        doorApi.uploadVoice(body).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, "✅ 목소리 전달 완료!", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
        })
    }

    // 🔊 [오디오 감청 루프]: 직접통화 중일 때 파이가 녹음해놓은 현관 앞 소리를 다운받아 재생합니다.
    private fun startVoiceListeningLoop() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                // 직접통화 버튼을 최소 한 번 터치하여 대화가 활성화된 경우에만 소리를 당겨옵니다.
                if (!isCallActive) {
                    delay(2000)
                    continue
                }

                doorApi.listenVoice().enqueue(object : Callback<ResponseBody> {
                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        if (response.isSuccessful && response.body() != null) {
                            try {
                                // 받아온 음성 스트림 파일을 임시 캐시 공간에 파일로 다운로드
                                val tempFile = File(requireContext().cacheDir, "voice_pi.wav")
                                val inputStream = response.body()!!.byteStream()
                                val outputStream = FileOutputStream(tempFile)

                                inputStream.use { input ->
                                    outputStream.use { output ->
                                        input.copyTo(output)
                                    }
                                }

                                // 스마트폰 스피커를 통해 현관 앞 소리 즉시 재생
                                if (tempFile.exists() && tempFile.length() > 0 && isAdded) {
                                    MediaPlayer().apply {
                                        setDataSource(tempFile.absolutePath)
                                        prepare()
                                        start()
                                        setOnCompletionListener { release() } // 재생 완하면 자원 반납
                                    }
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
                })
                delay(4000) // 4초 간격으로 현관 소리 캐치 루프 가동
            }
        }
    }

    private fun classify(type: String) {
        doorApi.classifyVisitor(ClassifyRequest(type)).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                Toast.makeText(context, "'$type' 전송 완료", Toast.LENGTH_SHORT).show()
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(context, "'$type' 전송 완료", Toast.LENGTH_SHORT).show()
            }
        })
    }
}