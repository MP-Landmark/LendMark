package com.example.lendmark.ui.auth.signup

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.lendmark.R
import com.example.lendmark.databinding.FragmentSignupBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.functions.FirebaseFunctions

class SignupFragment : Fragment() {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SignupViewModel by viewModels()
    private val functions = FirebaseFunctions.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 학과 리스트 세팅
        viewModel.departments.observe(viewLifecycleOwner) { deptList ->
            if (deptList.isNotEmpty()) {
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    deptList
                )
                binding.autoDept.setAdapter(adapter)

                binding.autoDept.setOnItemClickListener { _, _, position, _ ->
                    val selectedDept = deptList[position]
                    Toast.makeText(requireContext(), "Selected: $selectedDept", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 이메일 인증 버튼 클릭 시
        binding.btnVerify.setOnClickListener {
            val emailId = binding.etEmailId.text.toString().trim()
            if (emailId.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter your school email ID.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val email = "$emailId@seoultech.ac.kr"

            functions
                .getHttpsCallable("sendVerificationCode")
                .call(hashMapOf("email" to email))
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Verification code sent to $email.", Toast.LENGTH_SHORT).show()
                    showVerificationDialog(email) // 다이얼로그 표시
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to send email: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // 회원가입 완료 버튼
        binding.btnSignupDone.setOnClickListener {
            val name = binding.etName.text.toString()
            val emailId = binding.etEmailId.text.toString().trim()
            val fullEmail = "$emailId@seoultech.ac.kr"
            val phone = binding.etPhone.text.toString()
            val dept = binding.autoDept.text.toString()
            val password = binding.etPassword.text.toString()
            val confirmPw = binding.etConfirmPassword.text.toString()

            viewModel.signup(name, fullEmail, phone, dept, password, confirmPw)
        }

        // 회원가입 성공 시
        viewModel.signupResult.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { success ->
                if (success) {
                    Toast.makeText(requireContext(), "Sign up successful!", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_signup_to_login)
                }
            }
        }

        // 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // ViewModel 메시지/에러 처리
        viewModel.errorMessage.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 이메일 인증 다이얼로그 표시
     */
    private fun showVerificationDialog(email: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_email_verify, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnClose)
        val btnConfirm = dialogView.findViewById<View>(R.id.btnConfirm)
        val etCode = dialogView.findViewById<TextInputEditText>(R.id.etCode)

        // 설명 문구 동적으로 이메일 넣기
        val tvDescription = dialogView.findViewById<android.widget.TextView>(R.id.tvDescription)
        tvDescription.text = "Please enter the verification code sent to $email."

        // 닫기 버튼
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // 확인 버튼
        btnConfirm.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.length != 6) {
                Toast.makeText(requireContext(), "Please enter a valid 6-digit code.", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.verifyCode(email, code)
                dialog.dismiss()
            }
        }

        // 검증 결과 처리
        viewModel.emailVerified.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { verified ->
                if (verified) {
                    Toast.makeText(requireContext(), "Email verified successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Incorrect verification code.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
