package com.example.lendmark.ui.notification

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.lendmark.databinding.DialogNotificationDetailBinding

class NotificationDetailDialog(
    private val item: NotificationItem
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogNotificationDetailBinding.inflate(LayoutInflater.from(context))

        // Set title and details
        binding.tvTitle.text = item.title
        binding.tvDetail.text = "Reservation at: ${item.location} (${item.startTime} - ${item.endTime})"

        // "Go to Reservation Details" button (functionality not yet implemented)
        binding.btnGoReservation.setOnClickListener {
            // TODO: Navigate to reservation details page (to be implemented later)
            dismiss()
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
