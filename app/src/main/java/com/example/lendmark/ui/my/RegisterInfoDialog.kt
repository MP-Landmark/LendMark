package com.example.lendmark.ui.my

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.lendmark.R
import com.example.lendmark.databinding.DialogRegisterInfoBinding
import com.google.android.material.chip.Chip

class RegisterInfoDialog(private val onRegister: (List<String>) -> Unit) : DialogFragment() {

    private var _binding: DialogRegisterInfoBinding? = null
    private val binding get() = _binding!!

    private val selectedFeatures = mutableListOf<String>()

    // Sample classroom features data
    private val allFeatures = listOf(
        "Beam projector available", "Computer room", "Large classroom", "Medium classroom",
        "Small classroom", "Has a chalkboard", "Has a whiteboard", "Air conditioning equipped",
        "Heating equipped", "Many windows", "Bright lighting", "Auditorium-style seating", "Has a stage"
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogRegisterInfoBinding.inflate(LayoutInflater.from(context))

        setupCheckboxes()
        updateSelectedInfo()

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnRegister.setOnClickListener {
            onRegister(selectedFeatures)
            dismiss()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        return dialog
    }

    private fun setupCheckboxes() {
        allFeatures.forEach { feature ->
            val checkBox = CheckBox(requireContext()).apply {
                text = feature
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedFeatures.add(feature)
                    } else {
                        selectedFeatures.remove(feature)
                    }
                    updateSelectedInfo()
                }
            }
            binding.llCheckboxContainer.addView(checkBox)
        }
    }

    private fun updateSelectedInfo() {
        // Update visibility of the selected info section
        binding.llSelectedInfo.visibility = if (selectedFeatures.isEmpty()) View.GONE else View.VISIBLE

        // Update the count text
        binding.tvSelectedCount.text = "Selected Info (${selectedFeatures.size})"

        // Update the chips
        binding.chipGroupSelected.removeAllViews()
        selectedFeatures.forEach { feature ->
            val chip = layoutInflater.inflate(R.layout.item_selected_feature_chip, binding.chipGroupSelected, false) as Chip
            chip.text = feature
            binding.chipGroupSelected.addView(chip)
        }

        // Update the register button state
        binding.btnRegister.isEnabled = selectedFeatures.isNotEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
