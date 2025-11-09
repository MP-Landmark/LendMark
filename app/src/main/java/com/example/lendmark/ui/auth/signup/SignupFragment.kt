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

        // Department Spinner (Temporary example data)
        val departments = listOf("Computer Science", "Industrial Engineering", "Mechanical Engineering", "Design")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, departments)
        binding.spinnerDept.adapter = adapter

        // Email verification button -> Show popup
        binding.btnVerify.setOnClickListener {
            val email = binding.etEmail.text.toString()

            if (email.isBlank()) {
                Toast.makeText(requireContext(), "Please enter your email.", Toast.LENGTH_SHORT).show()
            } else {
                // Show popup dialog
                val dialog = EmailVerifyDialog(email) { code ->
                    viewModel.verifyCode(email, code)  // Pass the verification code received from the popup
                }
                dialog.show(parentFragmentManager, "EmailVerifyDialog")
            }
        }

        // Sign up complete button
        binding.btnSignupDone.setOnClickListener {
            val name = binding.etName.text.toString()
            val email = binding.etEmail.text.toString()
            val phone = binding.etPhone.text.toString()
            val dept = binding.spinnerDept.selectedItem.toString()
            val password = binding.etPassword.text.toString()
            val confirmPw = binding.etConfirmPassword.text.toString()

            viewModel.signup(name, email, phone, dept, password, confirmPw)
        }

        // Result observer
        viewModel.signupResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(requireContext(), "Sign up successful!", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_signup_to_login)
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        // Back button
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
