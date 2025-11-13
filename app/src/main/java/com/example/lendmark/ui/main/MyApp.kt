package com.example.lendmark.ui.main

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk
import com.example.lendmark.R   // ✅ 추가

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 네이티브 앱 키로 초기화 (strings.xml에 넣어둔 값 사용)
        KakaoMapSdk.init(this, getString(R.string.kakao_native_app_key))
    }
}
