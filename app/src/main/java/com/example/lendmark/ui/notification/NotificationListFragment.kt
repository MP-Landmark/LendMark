package com.example.lendmark.ui.notification

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lendmark.databinding.FragmentNotificationListBinding

class NotificationListFragment : Fragment() {

    private var _binding: FragmentNotificationListBinding? = null
    private val binding get() = _binding!!

    // ViewModel 연결
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

        // 1. 저장된 설정 불러오기 (SharedPreferences)
        // 앱 내 저장소에서 "NotiSettings"라는 이름으로 설정값을 가져옵니다.
        val sharedPref = requireActivity().getSharedPreferences("NotiSettings", Context.MODE_PRIVATE)
        val isInAppOn = sharedPref.getBoolean("KEY_IN_APP", true) // 기본값은 켜짐(true)
        val isPushOn = sharedPref.getBoolean("KEY_PUSH", true)

        // 2. ViewModel 및 UI 초기 상태 설정
        viewModel.isInAppEnabled = isInAppOn   // ViewModel에 현재 상태 전달
        binding.switchInApp.isChecked = isInAppOn
        binding.switchPush.isChecked = isPushOn

        // 3. 어댑터 초기화 (클릭 시 다이얼로그 표시)
        adapter = NotificationAdapter(emptyList()) { item ->
            viewModel.selectNotification(item)
            NotificationDetailDialog(item).show(parentFragmentManager, "notification_detail")
        }

        // RecyclerView 설정
        binding.recyclerNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerNotifications.adapter = adapter

        // 4. ViewModel에서 알림 목록 관찰 (UI 업데이트)
        viewModel.notifications.observe(viewLifecycleOwner) { list ->
            adapter.updateList(list)
        }

        // 5. 스위치 리스너 설정 (사용자가 스위치를 딸깍거릴 때)

        // [인앱 알림 스위치]
        binding.switchInApp.setOnCheckedChangeListener { _, isChecked ->
            // (1) 설정 저장
            sharedPref.edit().putBoolean("KEY_IN_APP", isChecked).apply()

            // (2) ViewModel에 변경된 상태 알림 및 목록 새로고침
            viewModel.isInAppEnabled = isChecked
            viewModel.checkReservationsAndCreateNotifications()

            // (3) 안내 메시지 (영어 수정)
            val msg = if (isChecked) "In-app notifications enabled." else "In-app notifications disabled."
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // [푸시 알림 스위치]
        binding.switchPush.setOnCheckedChangeListener { _, isChecked ->
            // (1) 설정 저장
            sharedPref.edit().putBoolean("KEY_PUSH", isChecked).apply()

            // (2) 안내 메시지 (영어 수정)
            val msg = if (isChecked) "Push notifications enabled." else "Push notifications disabled."
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // 6. 화면에 들어왔을 때 데이터 한 번 불러오기 (설정에 맞춰서)
        viewModel.checkReservationsAndCreateNotifications()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}