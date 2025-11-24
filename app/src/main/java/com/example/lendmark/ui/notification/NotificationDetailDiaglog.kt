package com.example.lendmark.ui.notification

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.lendmark.databinding.DialogNotificationDetailBinding
import com.example.lendmark.ui.my.Reservation // Import Reservation data class
import com.example.lendmark.ui.my.ReservationDetailDialog // Import the target dialog

class NotificationDetailDialog(
    private val item: NotificationItem
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogNotificationDetailBinding.inflate(LayoutInflater.from(context))

        // Set title and details
        binding.tvTitle.text = item.title
        binding.tvDetail.text = "Reservation at: ${item.location} (${item.startTime} - ${item.endTime})"

        // Connect the "Go to Reservation Details" button
        binding.btnGoReservation.setOnClickListener {
            // Dismiss the current simple dialog
            dismiss()

            // Create a temporary Reservation object from the NotificationItem's data
            val reservationData = Reservation(
                id = item.id,
                building = item.location.split(" ").firstOrNull() ?: item.location, // Simple name extraction
                room = item.location,
                date = item.date,
                time = "${item.startTime} - ${item.endTime}",
                attendees = 0, // This info isn't in the notification
                purpose = "(Details in My Reservations)", // This info isn't in the notification
                status = "Approved", // Assume a status to show the correct buttons
                isCancelled = false
            )

            // Create and show the detailed dialog, passing all required parameters
            val detailDialog = ReservationDetailDialog(
                reservation = reservationData,
                onCancelClick = { /* No action needed from notification dialog */ },
                onRegisterClick = { /* No action needed from notification dialog */ }
            )
            detailDialog.show(parentFragmentManager, "ReservationDetailDialog")
        }

        // OK button
        binding.btnConfirm.setOnClickListener {
            dismiss()
        }

        // Apply dialog style
        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }
}
