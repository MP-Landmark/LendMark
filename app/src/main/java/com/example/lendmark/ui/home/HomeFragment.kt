package com.example.lendmark.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lendmark.R
import com.example.lendmark.databinding.FragmentHomeBinding
import com.example.lendmark.ui.chatbot.*
import com.example.lendmark.ui.home.adapter.AnnouncementAdapter
import com.example.lendmark.ui.home.adapter.FrequentlyUsedRoomsAdapter
import com.example.lendmark.ui.my.ReservationDetailDialogFS
import com.example.lendmark.ui.my.ReservationFS
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by viewModels()
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
        observeViewModel()
        homeViewModel.loadHomeData()
    }

    private fun setupUI() {
        binding.recyclerFrequentlyUsed.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        binding.recyclerBuildingList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        binding.tvSeeAllBuildings.setOnClickListener {
            activity?.findViewById<BottomNavigationView>(R.id.bottomNav)?.selectedItemId = R.id.nav_book
        }

        binding.ivChatbot.setOnClickListener {
            val intent = Intent(requireContext(), ChatBotActivity::class.java)
            startActivity(intent)
        }
    }


    private fun observeViewModel() {

        homeViewModel.announcements.observe(viewLifecycleOwner) {
            binding.viewPagerAnnouncements.adapter = AnnouncementAdapter(it)
        }

        homeViewModel.frequentlyUsedRooms.observe(viewLifecycleOwner) {
            binding.recyclerFrequentlyUsed.adapter = FrequentlyUsedRoomsAdapter(it)
        }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
