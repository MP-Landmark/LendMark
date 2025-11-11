package com.example.lendmark.ui.my

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.lendmark.R
import com.example.lendmark.databinding.FragmentMyPageBinding
import com.example.lendmark.ui.auth.AuthActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth

class MyPageFragment : Fragment() {
    private var _binding: FragmentMyPageBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyPageBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toggleGroup =
            view.findViewById<MaterialButtonToggleGroup>(R.id.my_toggle_group)
        val btnInfo = view.findViewById<MaterialButton>(R.id.btn_my_info)
        val btnReservation = view.findViewById<MaterialButton>(R.id.btn_my_reservation)
        val btnFavorite = view.findViewById<MaterialButton>(R.id.btn_my_favorite)

        // 기본: 내 정보 탭
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.my_content_container, MyInfoFragment())
                .commit()
            toggleGroup.check(btnInfo.id)
        }

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val fragment = when (checkedId) {
                R.id.btn_my_info -> MyInfoFragment()
                R.id.btn_my_reservation -> MyReservationFragment()
                R.id.btn_my_favorite -> MyFavoriteFragment()
                else -> null
            }

            fragment?.let {
                childFragmentManager.beginTransaction()
                    .replace(R.id.my_content_container, it)
                    .commit()
            }
        }

        binding.tvLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()

            // 로그인 화면(AuthActivity)로 이동
            val intent = Intent(requireContext(), AuthActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            requireActivity().finish()  // 현재 액티비티 종료 (MainActivity 닫기)
        }

    }
}
