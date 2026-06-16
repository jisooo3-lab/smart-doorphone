package com.app.dingdong

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.app.dingdong.ui.HomeFragment
import com.app.dingdong.ui.HistoryFragment
import com.app.dingdong.ui.DisplayFragment
import com.app.dingdong.ui.SettingsFragment

class MainActivity : AppCompatActivity() {

    // 🌟 [수정 포인트 1]: 프래그먼트 객체들을 액티비티가 딱 한 번만 만들어서 보관하도록 인스턴스화합니다.
    private val homeFragment by lazy { HomeFragment() }
    private val historyFragment by lazy { HistoryFragment() }
    private val displayFragment by lazy { DisplayFragment() }
    private val settingsFragment by lazy { SettingsFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // 1. 앱이 처음 켜졌을 때 보관 중인 진짜 homeFragment 인스턴스를 깔아줍니다.
        if (savedInstanceState == null) {
            replaceFragment(homeFragment)
        }

        // 2. 하단 탭을 눌렀을 때 새 객체를 '생성()'하지 않고, 만들어둔 상자에서 꺼내 재활용합니다.
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    replaceFragment(homeFragment) // 🌟 재활용
                    true
                }
                R.id.nav_history -> {
                    replaceFragment(historyFragment) // 🌟 재활용
                    true
                }
                R.id.nav_oled -> {
                    replaceFragment(displayFragment) // 🌟 재활용
                    true
                }
                R.id.nav_settings -> {
                    replaceFragment(settingsFragment) // 🌟 재활용
                    true
                }
                else -> false
            }
        }
    }

    // 3. 부품을 교체해 주는 함수 (이제 인스턴스가 고정되어 안전합니다)
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}