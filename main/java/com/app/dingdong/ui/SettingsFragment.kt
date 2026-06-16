package com.app.dingdong.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.app.dingdong.R
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 스위치 버튼들을 찾아서 설정합니다.
        val switchAI = view.findViewById<SwitchMaterial>(R.id.switchAI)
        val switchAutoVoice = view.findViewById<SwitchMaterial>(R.id.switchAutoVoice)
        val layoutWaitTime = view.findViewById<View>(R.id.layoutWaitTime)
        val tvWaitTime = view.findViewById<TextView>(R.id.tvWaitTime)

        // 스위치를 켜고 끌 때마다 안내 메시지를 띄워줍니다.
        switchAI.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "활성화" else "비활성화"
            Toast.makeText(requireContext(), "AI 자동 분류가 $status 되었습니다.", Toast.LENGTH_SHORT).show()
        }

        switchAutoVoice.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "켜짐" else "꺼짐"
            Toast.makeText(requireContext(), "'누구세요?' 자동 재생 기능이 $status 상태입니다.", Toast.LENGTH_SHORT).show()
        }

        // 대기 시간 클릭 시 알림 형태의 시간 조절 팝업 실행
        layoutWaitTime.setOnClickListener {
            // 10초부터 5분(300초)까지 총 30개의 선택지 생성
            val steps = 30
            val displayValues = Array(steps) { i ->
                val totalSeconds = (i + 1) * 10
                if (totalSeconds >= 60) {
                    val minutes = totalSeconds / 60
                    val remainingSeconds = totalSeconds % 60
                    if (remainingSeconds == 0) "${minutes}분" else "${minutes}분 ${remainingSeconds}초"
                } else {
                    "${totalSeconds}초"
                }
            }

            // 회전형 휠 피커 객체 세팅
            val picker = NumberPicker(requireContext()).apply {
                minValue = 0
                maxValue = steps - 1
                displayedValues = displayValues
                wrapSelectorWheel = false

                // 현재 설정되어 있는 시간 파악 후 초기 스크롤 위치 맞추기
                val currentText = tvWaitTime.text.toString()
                val matchedIndex = displayValues.indexOf(currentText)
                value = if (matchedIndex != -1) matchedIndex else 2 // 기본값 30초(index 2)
            }

            // 다이얼로그 내부 레이아웃이 찢어지지 않도록 깔끔하게 컨테이너에 배치
            val pickerContainer = FrameLayout(requireContext()).apply {
                val params = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                }
                addView(picker, params)
            }

            // 다이얼로그 팝업 빌드
            AlertDialog.Builder(requireContext())
                .setTitle("응답 대기 시간 설정")
                .setMessage("알림 발생 후 AI 응대까지 대기할 시간을 선택하세요 (10초 단위).")
                .setView(pickerContainer)
                .setPositiveButton("확인") { _, _ ->
                    val selectedTimeText = displayValues[picker.value]
                    tvWaitTime.text = selectedTimeText
                    Toast.makeText(requireContext(), "대기 시간이 ${selectedTimeText}으로 변경되었습니다.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }
}