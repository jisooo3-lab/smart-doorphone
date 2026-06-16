package com.app.dingdong.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.dingdong.R
import com.app.dingdong.api.DoorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DisplayFragment : Fragment() {

    private lateinit var tvMain: TextView
    private lateinit var tvSub: TextView
    private lateinit var rvDisplayLog: RecyclerView

    // 이전 전광판 변경 기록들을 누적해서 담아둘 안드로이드 자체 리스트
    private val displayHistoryList = mutableListOf<Pair<String, String>>()
    private lateinit var logAdapter: DisplayLogAdapter

    // 라즈베리파이 IP 주소 (HomeFragment와 동일하게 세팅하세요!)
    private val raspberryPiIp = "172.20.10.9"

    private val doorApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://$raspberryPiIp:5000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DoorApi::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_display, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. UI 뷰 컴포넌트 매칭
        tvMain = view.findViewById(R.id.tvDisplayMain)
        tvSub = view.findViewById(R.id.tvDisplaySub)
        rvDisplayLog = view.findViewById(R.id.rvDisplayLog)

        // 2. 하단 리사이클러뷰 세팅
        rvDisplayLog.layoutManager = LinearLayoutManager(requireContext())
        logAdapter = DisplayLogAdapter(displayHistoryList)
        rvDisplayLog.adapter = logAdapter

        // 3. 🌟 라즈베리파이 현관 디스플레이 상태 실시간 추적 루프 가동
        startDisplaySyncLoop()
    }

    // 🔄 라즈베리파이 전광판과 앱 화면을 1초 간격으로 무한 동기화하는 코루틴 루프
    private fun startDisplaySyncLoop() {
        lifecycleScope.launch(Dispatchers.IO) {
            var lastMessage = "" // 중복 로그 누적 방지용 가드 변수

            while (true) {
                try {
                    // 파이 백엔드의 /display_status 엔드포인트를 동기식으로 호출
                    val response = doorApi.getDisplayStatus().execute()

                    if (response.isSuccessful && response.body() != null) {
                        val statusData = response.body()!!
                        val fullMessage = statusData.message // 예: "-현재상태- 대기 중... 방문자 없음"
                        val hexColor = statusData.bg_color  // 예: "#2C3E50"

                        // UI 갱신은 반드시 메인(Main) 스레드에서 처리해야 안전합니다.
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                // 상단 텍스트뷰에 현재 라즈베리파이 디스플레이 상태 그대로 미러링 바인딩
                                tvMain.text = "현관 전광판 현황"
                                tvSub.text = fullMessage

                                // 파이의 배경색 피드백을 인식하여 앱의 글씨 색상도 센스있게 변경 가능!
                                if (hexColor == "#C0392B" || hexColor == "#D35400") {
                                    tvSub.setTextColor(Color.RED) // 경고 상황은 빨간 글씨
                                } else if (hexColor == "#27AE60") {
                                    tvSub.setTextColor(Color.parseColor("#27AE60")) // 안전 상황은 초록 글씨
                                } else {
                                    tvSub.setTextColor(Color.BLACK) // 기본 블랙
                                }

                                // 🌟 [로그 누적 엔진]: 새로운 전광판 변경 메시지가 수신되었다면 하단 기록 리스트에 실시간 추가!
                                if (fullMessage.isNotEmpty() && fullMessage != lastMessage) {
                                    val currentTime = SimpleDateFormat("a h:mm", Locale.KOREA).format(Date())

                                    // 리스트 맨 위에 새로운 기록 꽂아넣기
                                    displayHistoryList.add(0, Pair(currentTime, fullMessage))
                                    logAdapter.notifyItemInserted(0)
                                    rvDisplayLog.scrollToPosition(0) // 스크롤을 맨 위로 올림

                                    lastMessage = fullMessage // 상태 기억
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 1초 주기로 파이 전광판 상태 체킹 연동
                delay(1000)
            }
        }
    }
}

// 하단 리스트에 데이터를 예쁘게 꽂아주는 어댑터 (동적 변동 리스트 매칭 완료)
class DisplayLogAdapter(private val logs: List<Pair<String, String>>) :
    RecyclerView.Adapter<DisplayLogAdapter.LogViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_display_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.tvTime.text = logs[position].first
        holder.tvMessage.text = logs[position].second
    }

    override fun getItemCount() = logs.size

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvLogTime)
        val tvMessage: TextView = view.findViewById(R.id.tvLogMessage)
    }
}