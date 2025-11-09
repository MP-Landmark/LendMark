package com.example.lendmark.ui.my

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.lendmark.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class MyPageFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my_page, container, false)
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
    }
}
