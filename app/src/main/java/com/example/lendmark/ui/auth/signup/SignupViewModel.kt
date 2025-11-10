package com.example.lendmark.ui.auth.signup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

class SignupViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    private val functions = FirebaseFunctions.getInstance()

    private val _departments = MutableLiveData<List<String>>()
    val departments: LiveData<List<String>> get() = _departments

    private val _signupResult = MutableLiveData<Boolean>()
    val signupResult: LiveData<Boolean> get() = _signupResult

    private val _emailVerified = MutableLiveData<Boolean>()
    val emailVerified: LiveData<Boolean> get() = _emailVerified

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> get() = _errorMessage


    init {
        loadDepartments()
    }

    // Firestore에서 학과 리스트 불러오기
    private fun loadDepartments() {
        db.collection("timetables")
            .document("2025-fall")
            .collection("departments")
            .get()
            .addOnSuccessListener { result ->
                val deptList = result.documents.mapNotNull { it.getString("department") }
                _departments.value = deptList
            }
            .addOnFailureListener { e ->
                _errorMessage.value = "Failed to load departments: ${e.message}"
            }
    }

    // 회원가입
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

        val userId = email.substringBefore("@")
        val userData = hashMapOf(
            "name" to name,
            "email" to email,
            "phone" to phone,
            "dept" to dept,
            "password" to pw
        )

        db.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                _signupResult.value = true
            }
            .addOnFailureListener {
                _errorMessage.value = "Sign up failed: ${it.message}"
            }
    }

    fun requestEmailCode(email: String) {
        functions
            .getHttpsCallable("sendVerificationCode")
            .call(hashMapOf("email" to email))
            .addOnSuccessListener {
                _errorMessage.value = "Authentication Code Sent to ${email}."
            }
            .addOnFailureListener {
                _errorMessage.value = "Failed to send email: ${it.message}"
            }
    }


    fun verifyCode(email: String, code: String) {
        functions
            .getHttpsCallable("verifyEmailCode")
            .call(hashMapOf("email" to email, "code" to code))
            .addOnSuccessListener { result ->
                val data = result.data as Map<*, *>
                val ok = data["ok"] as? Boolean ?: false
                val reason = data["reason"] as? String ?: ""

                if (ok) {
                    _emailVerified.value = true
                    _errorMessage.value = "Email verified successfully."
                } else {
                    _emailVerified.value = false
                    _errorMessage.value = when (reason) {
                        "INVALID" -> "The verification code is incorrect."
                        "EXPIRED" -> "The verification code has expired."
                        "NOT_FOUND" -> "No verification request found for this email."
                        else -> "Verification failed. Please try again."
                    }
                }
            }
            .addOnFailureListener {
                _errorMessage.value = "Error verifying code: ${it.message}"
            }
    }

}


