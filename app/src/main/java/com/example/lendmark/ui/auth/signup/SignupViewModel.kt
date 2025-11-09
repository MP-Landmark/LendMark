package com.example.lendmark.ui.auth.signup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SignupViewModel : ViewModel() {

    private val _signupResult = MutableLiveData<Boolean>()
    val signupResult: LiveData<Boolean> get() = _signupResult

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> get() = _errorMessage

    // Called when the email verification button is clicked
    fun sendVerificationCode(email: String) {
        if (email.isBlank()) {
            _errorMessage.value = "Please enter your email."
            return
        }
        // For testing purposes before actual server integration
        _errorMessage.value = "A verification code has been sent to ${email}."
    }

    // Signup logic
    fun signup(name: String, email: String, phone: String, dept: String, pw: String, confirmPw: String) {
        if (name.isBlank() || email.isBlank() || phone.isBlank() || pw.isBlank() || confirmPw.isBlank()) {
            _errorMessage.value = "Please fill in all fields."
            return
        }
        if (pw != confirmPw) {
            _errorMessage.value = "Passwords do not match."
            return
        }
        if (pw.length < 6) {
            _errorMessage.value = "Password must be at least 6 characters long."
            return
        }

        // TODO: Integrate with actual server or Firebase
        _signupResult.value = true
    }

    fun verifyCode(email: String, code: String) {
        if (code.length != 6) {
            _errorMessage.value = "Verification code must be 6 digits."
            return
        }
        _errorMessage.value = "$email has been verified."
    }
}
