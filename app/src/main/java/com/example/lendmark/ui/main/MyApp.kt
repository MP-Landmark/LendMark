package com.example.lendmark.ui.main

import android.app.Application
import android.os.Build
import com.kakao.vectormap.KakaoMapSdk
import com.example.lendmark.R

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        KakaoMapSdk.init(this, getString(R.string.kakao_native_app_key))
    }
}
