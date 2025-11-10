package com.example.lendmark.ui.auth.signup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.lendmark.R
import com.example.lendmark.databinding.FragmentSignupBinding
import com.google.firebase.functions.FirebaseFunctions

class SignupFragment : Fragment() {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SignupViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val functions = FirebaseFunctions.getInstance()

        // Firestore에서 불러온 학과 리스트 표시
        viewModel.departments.observe(viewLifecycleOwner) { deptList ->
            if (deptList.isNotEmpty()) {
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, deptList)
                binding.autoDept.setAdapter(adapter)

                // 선택 시 바로 반응
                binding.autoDept.setOnItemClickListener { _, _, position, _ ->
                    val selectedDept = deptList[position]
                    Toast.makeText(requireContext(), "Selected: $selectedDept", Toast.LENGTH_SHORT).show()
                }
            }
        }


        // 이메일 인증 버튼 클릭 시

        binding.btnVerify.setOnClickListener {
            val emailId = binding.etEmailId.text.toString().trim()
            val email = "$emailId@seoultech.ac.kr"

            functions
                .getHttpsCallable("sendVerificationCode")
                .call(hashMapOf("email" to email))
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Authentication code has been sent to $email.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to send e-mail: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }


        // 인증 코드 확인 버튼 클릭 시
        binding.btnConfirmCode.setOnClickListener {
            val emailId = binding.etEmailId.text.toString().trim()
            val fullEmail = "$emailId@seoultech.ac.kr"
            val code = binding.etVerifyCode.text.toString().trim()

            if (code.length != 6) {
                Toast.makeText(requireContext(), "Please enter a valid 6-digit code.", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.verifyCode(fullEmail, code)
            }
        }

        // ViewModel의 메시지 및 인증 상태 관찰
        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.emailVerified.observe(viewLifecycleOwner) { verified ->
            if (verified) {
                Toast.makeText(requireContext(), "Email verified successfully!", Toast.LENGTH_SHORT).show()
                // 예: 인증 성공 시 etVerifyCode 비활성화
                binding.etVerifyCode.isEnabled = false
                binding.btnConfirmCode.isEnabled = false
            }
        }

        // 인증 결과 (성공/실패) 메시지 표시
        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
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
        viewModel.signupResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(requireContext(), "Sign up successful!", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_signup_to_login)
            }
        }


        viewModel.signupResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(requireContext(), "Sign up successful!", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_signup_to_login)
            }
        }

        // 에러 메시지 처리
        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        // 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
