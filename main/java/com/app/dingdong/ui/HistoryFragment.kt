package com.app.dingdong.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.dingdong.R
import com.app.dingdong.api.DoorApi
import com.app.dingdong.model.MyVisitRecord // 🌟 임포트 체크
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HistoryFragment : Fragment() {

    private lateinit var rvHistory: RecyclerView

    private val doorApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://172.20.10.9:5001/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DoorApi::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvHistory = view.findViewById(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(context)
    }

    override fun onResume() {
        super.onResume()
        loadHistoryData()
    }

    private fun loadHistoryData() {
        doorApi.getHistory().enqueue(object : Callback<List<MyVisitRecord>> { // 🌟 MyVisitRecord 적용
            override fun onResponse(call: Call<List<MyVisitRecord>>, response: Response<List<MyVisitRecord>>) {
                if (!isAdded) return

                if (response.isSuccessful && response.body() != null) {
                    val historyList = response.body()!!
                    if (historyList.isNotEmpty()) {
                        val adapter = HistoryAdapter(historyList)
                        rvHistory.adapter = adapter
                    }
                }
            }

            override fun onFailure(call: Call<List<MyVisitRecord>>, t: Throwable) {
                if (!isAdded) return
                activity?.let {
                    Toast.makeText(it, "기록을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}