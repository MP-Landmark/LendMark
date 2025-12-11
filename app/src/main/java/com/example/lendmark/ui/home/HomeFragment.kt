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
import com.example.lendmark.ui.home.adapter.*
import com.example.lendmark.ui.my.ConfirmCancelDialog
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

    override fun onResume() {
        super.onResume()

        binding.searchBar.text.clear()
        binding.searchBar.clearFocus()

        homeViewModel.clearSearchResults()
        binding.recyclerSearchResults.visibility = View.GONE
        binding.recyclerSearchResults.adapter = null
    }

    // -------------------------------------------------------------------------
    //  UI 세팅
    // -------------------------------------------------------------------------
    private fun setupUI() {

        binding.recyclerFrequentlyUsed.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        binding.recyclerRecentlyViewed.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        binding.recyclerBuildingList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        binding.recyclerSearchResults.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        // "See all" 클릭 → Book 탭 이동
        binding.tvSeeAllBuildings.setOnClickListener {
            navigateToBookingTab(null)
        }

        // 챗봇
        binding.ivChatbot.setOnClickListener {
            startActivity(Intent(requireContext(), ChatBotActivity::class.java))
        }

        // 화면 터치하면 검색 결과 숨기기
        binding.contentLayout.setOnClickListener {
            hideKeyboard()
            binding.searchBar.clearFocus()

            homeViewModel.clearSearchResults()
            binding.recyclerSearchResults.visibility = View.GONE
            binding.recyclerSearchResults.adapter = null
        }
    }

    // -------------------------------------------------------------------------
    //  검색
    // -------------------------------------------------------------------------
    private fun setupSearch() {
        binding.searchBar.setOnEditorActionListener { v, actionId, event ->

            val isEnterKey =
                event != null &&
                        event.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                        event.action == android.view.KeyEvent.ACTION_DOWN

            val isSearchAction =
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                        actionId == EditorInfo.IME_ACTION_DONE

            if (isEnterKey || isSearchAction) {
                homeViewModel.searchBuilding(v.text.toString())
                hideKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    // -------------------------------------------------------------------------
    //  ViewModel Observe
    // -------------------------------------------------------------------------
    private fun observeViewModel() {

        // 공지 슬라이드
        homeViewModel.announcementSlides.observe(viewLifecycleOwner) {
            binding.viewPagerAnnouncements.adapter =
                AnnouncementAdapter(it, onReviewClick = { navigateToReviewPage() })
        }

        // 자주 이용한 방
        homeViewModel.frequentlyUsedRooms.observe(viewLifecycleOwner) {
            binding.recyclerFrequentlyUsed.adapter = FrequentlyUsedRoomsAdapter(it)
        }

        // 다가오는 예약 알림
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

        // 최근 본 방
        homeViewModel.recentViewedRooms.observe(viewLifecycleOwner) { rooms ->
            binding.recyclerRecentlyViewed.adapter = RecentlyViewedRoomsAdapter(rooms)
            binding.recyclerRecentlyViewed.visibility =
                if (rooms.isEmpty()) View.GONE else View.VISIBLE
        }

        // ------------------------------------------------------------
        // 홈 빌딩 리스트 (랜덤 3개)
        // ------------------------------------------------------------
        homeViewModel.homeBuildings.observe(viewLifecycleOwner) { buildings ->

            binding.recyclerBuildingList.adapter =
                HomeBuildingAdapter(buildings) { selectedBuilding ->
                    navigateToBookingTab(selectedBuilding.name)
                }
        }

        // ------------------------------------------------------------
        // 검색 결과
        // ------------------------------------------------------------
        homeViewModel.searchResults.observe(viewLifecycleOwner) { results ->
            if (results.isEmpty()) {
                binding.recyclerSearchResults.visibility = View.GONE
                return@observe
            }

            binding.recyclerSearchResults.visibility = View.VISIBLE
            binding.recyclerSearchResults.adapter =
                SearchResultsAdapter(results) { buildingName ->
                    navigateToBookingTab(buildingName)
                }
        }
    }

    // -------------------------------------------------------------------------
    //  BOOK 탭 이동
    // -------------------------------------------------------------------------
    private fun navigateToBookingTab(buildingName: String?) {

        if (buildingName != null && isAdded) {
            parentFragmentManager.setFragmentResult(
                "search_request",
                bundleOf("selectedBuilding" to buildingName)
            )
        }

        activity?.findViewById<BottomNavigationView>(R.id.bottomNav)
            ?.selectedItemId = R.id.nav_book
    }

    // -------------------------------------------------------------------------
    //  예약 상세 팝업
    // -------------------------------------------------------------------------
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
                    onCancelClick = { showCancelConfirmationDialog(reservation.id) },
                    onRegisterClick = { updateStatus(reservation.id, "finished") }
                ).show(parentFragmentManager, "ReservationDetailDialogFS")
            }
    }

    private fun showCancelConfirmationDialog(reservationId: String) {
        val dialog = ConfirmCancelDialog {
            updateStatus(reservationId, "canceled")
        }
        dialog.show(parentFragmentManager, "ConfirmCancelDialog")
    }

    private fun updateStatus(id: String, status: String) {
        db.collection("reservations").document(id)
            .update("status", status)
    }

    private fun navigateToReviewPage() {
        activity?.findViewById<BottomNavigationView>(R.id.bottomNav)
            ?.selectedItemId = R.id.nav_my

        parentFragmentManager.setFragmentResult(
            "review_request",
            bundleOf("openReview" to true)
        )
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        imm.hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
