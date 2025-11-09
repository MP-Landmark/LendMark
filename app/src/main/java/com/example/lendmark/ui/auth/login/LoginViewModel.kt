package com.example.lendmark.ui.auth.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LoginViewModel : ViewModel() {

    // 로그인 성공 여부 상태 저장
    private val _loginResult = MutableLiveData<Boolean>()
    val loginResult: LiveData<Boolean> get() = _loginResult

    // 에러 메시지 상태
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> get() = _errorMessage

    /**
     * 로그인 처리 함수
     */
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = "Please enter both email and password."
            return
        }

        // 임시 테스트용 로그인 로직
        if (email == "test@lendmark.com" && password == "1234") {
            _loginResult.value = true
        } else {
            _loginResult.value = false
            _errorMessage.value = "The email or password is not correct."
        }
    }
}
