package com.example.lendmark.ui.auth.findaccount

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.lendmark.R
import com.example.lendmark.databinding.FragmentFindAccountBinding

class FindAccountFragment : Fragment() {

    private var _binding: FragmentFindAccountBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFindAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set initial state: Find ID is selected
        binding.tabToggleGroup.check(R.id.btnFindIdTab)
        showFindIdLayout()

        // Set listener for the toggle group
        binding.tabToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnFindIdTab -> showFindIdLayout()
                    R.id.btnFindPwTab -> showFindPwLayout()
                }
            }
        }

        // Back button listener
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun showFindIdLayout() {
        binding.layoutFindId.visibility = View.VISIBLE
        binding.layoutFindPw.visibility = View.GONE
    }

    private fun showFindPwLayout() {
        binding.layoutFindId.visibility = View.GONE
        binding.layoutFindPw.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
