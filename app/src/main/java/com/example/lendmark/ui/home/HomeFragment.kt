package com.example.lendmark.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lendmark.R
import com.example.lendmark.databinding.FragmentHomeBinding
import com.example.lendmark.ui.chatbot.ChatBotActivity
import com.example.lendmark.ui.home.adapter.AnnouncementAdapter
import com.example.lendmark.ui.home.adapter.FrequentlyUsedRoomsAdapter
import com.example.lendmark.ui.home.adapter.RecentlyViewedRoomsAdapter
import com.example.lendmark.ui.home.adapter.SearchResultsAdapter
import com.example.lendmark.ui.my.ReservationDetailDialogFS
import com.example.lendmark.ui.my.ReservationFS
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupSearch()
        observeViewModel()
        homeViewModel.loadHomeData()
    }

    // 화면으로 돌아왔을 때 검색 상태 초기화
    override fun onResume() {
        super.onResume()
        binding.searchBar.text.clear()
        binding.searchBar.clearFocus()
        homeViewModel.clearSearchResults() // ViewModel에 이 함수가 있어야 합니다.
        binding.recyclerSearchResults.visibility = View.GONE
    }

    private fun setupUI() {
        // 1. 자주 쓰는 강의실 (가로)
        binding.recyclerFrequentlyUsed.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        // 2. 최근 본 강의실 (가로)
        binding.recyclerRecentlyViewed.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        // 3. 건물 목록 (맨 아래, 가로)
        binding.recyclerBuildingList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        // 4. 검색 결과 리스트 (세로 - 검색창 바로 아래)
        binding.recyclerSearchResults.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        // See All 클릭 시 예약 탭 이동
        binding.tvSeeAllBuildings.setOnClickListener {
            navigateToBookingTab(null)
        }

        // 챗봇 버튼
        binding.ivChatbot.setOnClickListener {
            val intent = Intent(requireContext(), ChatBotActivity::class.java)
            startActivity(intent)
        }

        // ⭐ 빈 공간(배경) 터치 시 검색 모드 종료 및 키보드 숨김
        // XML의 LinearLayout ID가 contentLayout 이어야 작동합니다.
        binding.contentLayout.setOnClickListener {
            hideKeyboard()
            binding.searchBar.clearFocus()
            homeViewModel.clearSearchResults()
            binding.recyclerSearchResults.visibility = View.GONE
        }
    }

    private fun setupSearch() {
        // 엔터키 리스너
        binding.searchBar.setOnEditorActionListener { v, actionId, event ->
            val isEnterKey = (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)
            val isSearchAction = (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE)

            if (isSearchAction || isEnterKey) {
                val query = v.text.toString()
                homeViewModel.searchBuilding(query)
                hideKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun observeViewModel() {
        // 공지사항 관찰
        homeViewModel.announcements.observe(viewLifecycleOwner) {
            binding.viewPagerAnnouncements.adapter = AnnouncementAdapter(it)
        }

        // 자주 쓰는 강의실 관찰
        homeViewModel.frequentlyUsedRooms.observe(viewLifecycleOwner) {
            binding.recyclerFrequentlyUsed.adapter = FrequentlyUsedRoomsAdapter(it)
        }

        // 다가오는 예약 관찰
        homeViewModel.upcomingReservation.observe(viewLifecycleOwner) { info ->
            if (info == null) {
                binding.includedUpcomingReservation.root.visibility = View.GONE
                return@observe
            }
            binding.includedUpcomingReservation.root.visibility = View.VISIBLE
            binding.includedUpcomingReservation.tvUpcomingReservationDetails.text =
                "${info.roomName} • ${info.time}"

            binding.includedUpcomingReservation.tvSeeDetails.setOnClickListener {
                loadReservationAndShowDialog(info.reservationId)
            }
        }

        // 최근 본 강의실 관찰
        homeViewModel.recentViewedRooms.observe(viewLifecycleOwner) { rooms ->
            binding.recyclerRecentlyViewed.adapter = RecentlyViewedRoomsAdapter(rooms)
            binding.recyclerRecentlyViewed.visibility = if (rooms.isEmpty()) View.GONE else View.VISIBLE
        }

        // ⭐ 검색 결과 관찰
        homeViewModel.searchResults.observe(viewLifecycleOwner) { results ->
            if (results.isEmpty()) {
                binding.recyclerSearchResults.visibility = View.GONE
            } else {
                binding.recyclerSearchResults.visibility = View.VISIBLE

                // 검색 결과 클릭 이벤트 처리
                binding.recyclerSearchResults.adapter = SearchResultsAdapter(results) { buildingName ->
                    // 클릭한 건물 이름으로 예약 탭 이동
                    navigateToBookingTab(buildingName)
                }
            }
        }
    }

    private fun navigateToBookingTab(buildingName: String?) {
        // 1. 데이터 전달 (검색어가 있을 경우)
        if (buildingName != null) {
            parentFragmentManager.setFragmentResult(
                "search_request",
                bundleOf("selectedBuilding" to buildingName)
            )
        }

        // 2. 예약 탭(BuildingListFragment)으로 화면 전환
        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav?.selectedItemId = R.id.nav_book
    }

    private fun loadReservationAndShowDialog(reservationId: String) {
        db.collection("reservations").document(reservationId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                val reservation = ReservationFS(
                    id = doc.id,
                    buildingId = doc.getString("buildingId") ?: "",
                    roomId = doc.getString("roomId") ?: "",
                    date = doc.getString("date") ?: "",
                    day = doc.getString("day") ?: "",
                    periodStart = doc.getLong("periodStart")?.toInt() ?: 0,
                    periodEnd = doc.getLong("periodEnd")?.toInt() ?: 0,
                    attendees = doc.getLong("people")?.toInt() ?: 0,
                    purpose = doc.getString("purpose") ?: "",
                    status = doc.getString("status") ?: "approved"
                )

                ReservationDetailDialogFS(
                    reservation = reservation,
                    onCancelClick = { updateStatus(reservation.id, "canceled") },
                    onRegisterClick = { updateStatus(reservation.id, "finished") }
                ).show(parentFragmentManager, "ReservationDetailDialogFS")
            }
    }

    private fun updateStatus(id: String, status: String) {
        db.collection("reservations").document(id)
            .update("status", status)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}