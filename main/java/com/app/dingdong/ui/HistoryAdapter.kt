package com.app.dingdong.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.app.dingdong.R
import com.app.dingdong.model.MyVisitRecord // 🌟 새로 만든 깨끗한 상자 임포트!

class HistoryAdapter(private val historyList: List<MyVisitRecord>) : // 🌟 MyVisitRecord로 타입 매칭
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val record = historyList[position]

        // 🌟 시간 데이터 매칭 (오류 완전 소멸)
        holder.tvTime.text = record.timestamp ?: "시간 정보 없음"

        // 🌟 라벨 데이터 매칭 (오류 완전 소멸)
        val labelResult = record.label ?: "미지정"
        holder.tvBadge.text = labelResult

        // 🌟 코틀린 주석 규칙 준수 및 색상 설정
        when (labelResult) {
            "택배", "지인", "안전" -> {
                holder.tvBadge.setTextColor(Color.parseColor("#4CAF50")) // 초록색
            }
            "위험", "수상함" -> {
                holder.tvBadge.setTextColor(Color.parseColor("#F44336")) // 빨간색
            }
            else -> {
                holder.tvBadge.setTextColor(Color.parseColor("#F57C00")) // 주황색
            }
        }

        holder.tvDesc.text = "라즈베리파이 카메라 자동 감지 및 기록"
    }

    override fun getItemCount(): Int {
        return historyList.size
    }

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTime: TextView = itemView.findViewById(R.id.tvItemTime)
        val tvBadge: TextView = itemView.findViewById(R.id.tvItemBadge)
        val tvDesc: TextView = itemView.findViewById(R.id.tvItemDesc)
    }
}