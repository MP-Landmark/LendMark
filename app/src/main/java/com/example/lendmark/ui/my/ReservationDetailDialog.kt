package com.example.lendmark.ui.my

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.example.lendmark.R
import com.example.lendmark.databinding.DialogReservationDetailBinding

class ReservationDetailDialog(
    private val reservation: Reservation,
    private val onCancelClick: (Int) -> Unit,
    private val onRegisterClick: (Reservation) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogReservationDetailBinding.inflate(LayoutInflater.from(context))

        // Set reservation info
        binding.tvBuildingName.text = reservation.building
        binding.tvRoomName.text = reservation.room
        binding.tvDate.text = reservation.date
        binding.tvTime.text = reservation.time
        binding.tvAttendees.text = "${reservation.attendees} people"
        binding.tvPurpose.text = reservation.purpose

        // --- CLICK LISTENERS ---
        binding.btnClose.setOnClickListener { dismiss() } // Added this line

        binding.btnCancel.setOnClickListener {
            if (it.isEnabled) {
                onCancelClick(reservation.id)
                dismiss()
            }
        }

        // --- BUTTON STATE LOGIC ---
        when {
            reservation.isCancelled -> {
                binding.btnRegisterInfo.visibility = View.GONE
                binding.btnCancel.visibility = View.VISIBLE
                binding.btnCancel.text = "Cancelled"
                binding.btnCancel.isEnabled = false
                binding.btnCancel.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
                binding.btnCancel.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.gray)
            }
            reservation.status == "Finished" -> {
                binding.btnCancel.visibility = View.GONE
                binding.btnRegisterInfo.visibility = View.VISIBLE

                if (reservation.infoRegistered) {
                    binding.btnRegisterInfo.text = "Classroom Info Registered"
                    binding.btnRegisterInfo.isEnabled = false
                    binding.btnRegisterInfo.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.gray_light)
                    binding.btnRegisterInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
                } else {
                    binding.btnRegisterInfo.text = "Register Classroom Info"
                    binding.btnRegisterInfo.isEnabled = true
                    binding.btnRegisterInfo.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.colorPrimary)
                    binding.btnRegisterInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    binding.btnRegisterInfo.setOnClickListener {
                        onRegisterClick(reservation)
                        dismiss()
                    }
                }
            }
            else -> {
                binding.btnRegisterInfo.visibility = View.GONE
                binding.btnCancel.visibility = View.VISIBLE
                binding.btnCancel.text = "Cancel Reservation"
                binding.btnCancel.isEnabled = true
                binding.btnCancel.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                binding.btnCancel.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.red)
            }
        }

        val dialog = AlertDialog.Builder(requireActivity())
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }
}
