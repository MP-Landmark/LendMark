package com.example.lendmark.ui.notification

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lendmark.databinding.FragmentNotificationListBinding

class NotificationListFragment : Fragment() {

    private var _binding: FragmentNotificationListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotificationViewModel by viewModels()
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ⭐ [NEW] 알림 권한 요청 (안드로이드 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 권한 요청 팝업 띄우기
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // 1. 설정 불러오기
        val sharedPref = requireActivity().getSharedPreferences("NotiSettings", Context.MODE_PRIVATE)
        val isInAppOn = sharedPref.getBoolean("KEY_IN_APP", true)
        val isPushOn = sharedPref.getBoolean("KEY_PUSH", true)

        viewModel.isInAppEnabled = isInAppOn
        binding.switchInApp.isChecked = isInAppOn
        binding.switchPush.isChecked = isPushOn

        // 2. 어댑터 설정
        adapter = NotificationAdapter(emptyList()) { item ->
            viewModel.selectNotification(item)
            NotificationDetailDialog(item).show(parentFragmentManager, "notification_detail")
        }

        binding.recyclerNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerNotifications.adapter = adapter

        // 3. 관찰 (화면 리스트 업데이트)
        viewModel.notifications.observe(viewLifecycleOwner) { list ->
            adapter.updateList(list)
        }

        // 4. 스위치 리스너
        binding.switchInApp.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("KEY_IN_APP", isChecked).apply()
            viewModel.isInAppEnabled = isChecked
            viewModel.checkReservationsAndCreateNotifications()

            val msg = if (isChecked) "In-app notifications enabled." else "In-app notifications disabled."
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        binding.switchPush.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("KEY_PUSH", isChecked).apply()
            val msg = if (isChecked) "Push notifications enabled." else "Push notifications disabled."
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // 5. 데이터 로드
        viewModel.checkReservationsAndCreateNotifications()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}