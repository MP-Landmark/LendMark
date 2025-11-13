package com.example.lendmark.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lendmark.R
import com.example.lendmark.databinding.FragmentHomeBinding
import com.example.lendmark.ui.home.adapter.AnnouncementAdapter
import com.example.lendmark.ui.home.adapter.FrequentlyUsedRoomsAdapter
import com.example.lendmark.ui.my.Reservation
import com.example.lendmark.ui.my.ReservationDetailDialog
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by viewModels()

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

        binding.tvSeeAllBuildings.setOnClickListener {
            activity?.findViewById<BottomNavigationView>(R.id.bottomNav)?.selectedItemId = R.id.nav_book
        }
    }

    private fun observeViewModel() {
        homeViewModel.announcements.observe(viewLifecycleOwner) { list ->
            binding.viewPagerAnnouncements.adapter = AnnouncementAdapter(list)
        }

        homeViewModel.frequentlyUsedRooms.observe(viewLifecycleOwner) { list ->
            binding.recyclerFrequentlyUsed.adapter = FrequentlyUsedRoomsAdapter(list)
        }

        homeViewModel.upcomingReservation.observe(viewLifecycleOwner) { info ->
            if (info != null) {
                binding.includedUpcomingReservation.root.visibility = View.VISIBLE
                binding.includedUpcomingReservation.tvUpcomingReservationDetails.text = "${info.roomName} • ${info.time}"

                binding.includedUpcomingReservation.tvSeeDetails.setOnClickListener {
                    val dummyReservation = Reservation(
                        id = 0, 
                        building = info.roomName.split(" ").firstOrNull() ?: info.roomName,
                        room = info.roomName,
                        date = "", 
                        time = info.time,
                        attendees = 0, 
                        purpose = "", 
                        status = "Approved", 
                        isCancelled = false
                    )
                    ReservationDetailDialog(dummyReservation) {}.show(parentFragmentManager, "ReservationDetailDialog")
                }

            } else {
                binding.includedUpcomingReservation.root.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
