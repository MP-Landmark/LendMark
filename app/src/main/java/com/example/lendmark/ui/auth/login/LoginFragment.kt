package com.example.lendmark.ui.auth.login

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.lendmark.ui.main.MainActivity
import com.example.lendmark.R
import com.example.lendmark.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels() // ViewModel connection

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Login button click
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            viewModel.login(email, password)
        }

        // Navigate to Sign up
        binding.tvSignup.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_signup)
        }

        // Navigate to Find Account
        binding.tvFindAccount.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_findAccount)
        }

        // Observe login result
        viewModel.loginResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                // Login success -> Navigate to MainActivity
                startActivity(Intent(requireContext(), MainActivity::class.java))
                requireActivity().finish()
            }
        }

        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
