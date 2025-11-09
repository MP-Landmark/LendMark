package com.example.lendmark.ui.auth.signup

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.lendmark.databinding.DialogEmailVerifyBinding

class EmailVerifyDialog(
    private val email: String,
    private val onVerify: (String) -> Unit
) : DialogFragment() {

    private var _binding: DialogEmailVerifyBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogEmailVerifyBinding.inflate(LayoutInflater.from(context))

        val builder = AlertDialog.Builder(requireContext())
            .setView(binding.root)

        binding.tvDescription.text = "$email 로 전송된 인증 코드를 입력해주세요."

        // 인증 확인 버튼
        binding.btnConfirm.setOnClickListener {
            val code = binding.etCode.text.toString()
            onVerify(code)  // ✅ 부모 프래그먼트(SignupFragment)에 전달
            dismiss()
        }

        // 닫기 버튼 (X)
        binding.btnClose.setOnClickListener { dismiss() }

        return builder.create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
