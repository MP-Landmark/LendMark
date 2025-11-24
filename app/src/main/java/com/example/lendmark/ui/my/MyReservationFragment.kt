package com.example.lendmark.ui.my

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.lendmark.R
import com.example.lendmark.databinding.FragmentMyReservationBinding
import com.google.android.material.button.MaterialButton

data class Reservation(
    val id: Int,
    val building: String,
    val room: String,
    val date: String,
    val time: String,
    val attendees: Int,
    val purpose: String,
    var status: String, // Approved, Finished
    var isCancelled: Boolean = false,
    var infoRegistered: Boolean = false // New flag to track info registration
)

class MyReservationFragment : Fragment() {

    private var _binding: FragmentMyReservationBinding? = null
    private val binding get() = _binding!!

    private val reservations = mutableListOf(
        Reservation(1, "Frontier Hall", "Room 107", "2025-10-15", "13:00 - 15:00", 5, "Team project", "Approved"),
        Reservation(3, "Dasan Hall", "Room 301", "2025-10-10", "10:00 - 12:00", 3, "Study", "Finished")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyReservationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.filterGroup.check(R.id.filterAll)
        binding.filterGroup.setOnCheckedChangeListener { _, _ -> updateUI() }
        updateUI()

        binding.cardApproved.setOnClickListener { reservations.find { it.id == 1 }?.let { openDetailDialog(it) } }
        binding.cardFinished.setOnClickListener { reservations.find { it.id == 3 }?.let { openDetailDialog(it) } }

        binding.btnCancelApproved.setOnClickListener { showCancelConfirmationDialog(1) }
        
        binding.btnRegisterInfo.setOnClickListener { 
            val reservation = reservations.find { it.id == 3 }!!
            showRegisterInfoDialog(reservation)
        }
    }

    private fun openDetailDialog(reservation: Reservation) {
        val dialog = ReservationDetailDialog(
            reservation = reservation,
            onCancelClick = { reservationId -> showCancelConfirmationDialog(reservationId) },
            onRegisterClick = { res -> showRegisterInfoDialog(res) } // Connect the new callback
        )
        dialog.show(childFragmentManager, "ReservationDetailDialog")
    }

    private fun showCancelConfirmationDialog(reservationId: Int) {
        val onConfirm = {
            reservations.find { it.id == reservationId }?.apply {
                status = "Finished"
                isCancelled = true
            }
            updateUI()
        }
        ConfirmCancelDialog(onConfirm).show(parentFragmentManager, "ConfirmCancelDialog")
    }

    private fun showRegisterInfoDialog(reservation: Reservation) {
        val dialog = RegisterInfoDialog { selectedFeatures ->
            reservation.infoRegistered = true
            Toast.makeText(requireContext(), "Information registered!", Toast.LENGTH_SHORT).show()
            updateUI()
        }
        dialog.show(parentFragmentManager, "RegisterInfoDialog")
    }

    private fun updateUI() {
        val checkedId = binding.filterGroup.checkedChipId

        // --- Reservation 1 (Originally Approved) ---
        val res1 = reservations.find { it.id == 1 }!!
        val card1ShouldBeVisible = when (checkedId) {
            R.id.filterAll -> true
            R.id.filterApproved -> res1.status == "Approved" && !res1.isCancelled
            R.id.filterFinished -> res1.status == "Finished"
            else -> true
        }
        binding.cardApproved.visibility = if (card1ShouldBeVisible) View.VISIBLE else View.GONE
        updateCardStatus(res1, binding.tvStatusApproved)
        updateCancelButtonState(res1, binding.btnCancelApproved)

        // --- Reservation 3 (Originally Finished) ---
        val res3 = reservations.find { it.id == 3 }!!
        val card3ShouldBeVisible = when (checkedId) {
            R.id.filterAll -> true
            R.id.filterApproved -> false
            R.id.filterFinished -> res3.status == "Finished"
            else -> true
        }
        binding.cardFinished.visibility = if (card3ShouldBeVisible) View.VISIBLE else View.GONE
        updateCardStatus(res3, binding.tvStatusFinished)
        updateRegisterButtonState(res3, binding.btnRegisterInfo)
    }

    private fun updateCardStatus(reservation: Reservation, statusView: TextView) {
        statusView.text = if (reservation.isCancelled) "Cancelled" else reservation.status
        val (bgColor, textColor) = when {
            reservation.isCancelled -> R.drawable.bg_status_gray to R.color.gray_dark
            reservation.status == "Approved" -> R.drawable.bg_status_green to R.color.white
            reservation.status == "Finished" -> R.drawable.bg_status_gray to R.color.gray_dark
            else -> R.drawable.bg_status_gray to R.color.gray_dark
        }
        statusView.background = ContextCompat.getDrawable(requireContext(), bgColor)
        statusView.setTextColor(ContextCompat.getColor(requireContext(), textColor))
    }

    private fun updateCancelButtonState(reservation: Reservation, button: MaterialButton) {
        if (reservation.isCancelled || reservation.status == "Finished") {
            button.text = "Cancelled"
            button.isEnabled = false
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
            button.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.gray)
        } else {
            button.text = "Cancel Reservation"
            button.isEnabled = true
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
            button.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.red)
        }
    }
    
    private fun updateRegisterButtonState(reservation: Reservation, button: Button) {
        if (reservation.infoRegistered) {
            button.text = "Classroom Info Registered"
            button.isEnabled = false
            button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.gray_light)
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
        } else {
            button.text = "Register Classroom Info"
            button.isEnabled = true
            button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.colorPrimary)
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
