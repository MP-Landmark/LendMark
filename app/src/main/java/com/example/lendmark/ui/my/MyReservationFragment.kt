package com.example.lendmark.ui.my

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.lendmark.R
import com.example.lendmark.databinding.FragmentMyReservationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.TextView
import com.google.android.material.button.MaterialButton

class MyReservationFragment : Fragment() {

    private var _binding: FragmentMyReservationBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid

    private var reservationList: List<ReservationFS> = emptyList()
    private var buildingNameMap: Map<String, String> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyReservationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.filterGroup.check(R.id.filterAll)
        binding.filterGroup.setOnCheckedChangeListener { _, _ -> displayReservations() }

        loadReservations()
    }

    private fun loadReservations() {
        if (uid == null) return

        // 1. Load building names first
        db.collection("buildings").get()
            .addOnSuccessListener { buildingsSnapshot ->
                if (!isAdded) return@addOnSuccessListener
                buildingNameMap = buildingsSnapshot.documents.associate {
                    it.id to (it.getString("name") ?: "")
                }

                // 2. Then, load reservations
                db.collection("reservations")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        reservationList = snapshot.documents.map { doc ->
                            ReservationFS(
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
                        }.sortedByDescending { it.date } // Sort by most recent date

                        displayReservations()
                    }
                    .addOnFailureListener {
                        if (isAdded) Toast.makeText(requireContext(), "Failed to load reservations", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                if (isAdded) Toast.makeText(requireContext(), "Failed to load building data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayReservations() {
        val container = binding.reservationContainer
        container.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        val filtered = when (binding.filterGroup.checkedChipId) {
            R.id.filterApproved ->
                reservationList.filter { it.status == "approved" }

            R.id.filterFinished ->
                reservationList.filter {
                    it.status == "finished" ||
                            it.status == "canceled" ||
                            it.status == "expired"     // ← 여기가 핵심!
                }

            else -> reservationList
        }


        if (filtered.isEmpty()) {
            addEmptyMessage(container)
            return
        }

        filtered.forEach { reservation ->
            val card = inflater.inflate(R.layout.item_reservation, container, false)

            val tvBuildingRoom = card.findViewById<TextView>(R.id.tvBuildingRoom)
            val tvStatus = card.findViewById<TextView>(R.id.tvStatus)
            val tvDateTime = card.findViewById<TextView>(R.id.tvDateTime)
            val tvAttendees = card.findViewById<TextView>(R.id.tvAttendees)
            val tvPurpose = card.findViewById<TextView>(R.id.tvPurpose)
            val btnCancel = card.findViewById<MaterialButton>(R.id.btnCancel)
            val btnRegisterInfo = card.findViewById<MaterialButton>(R.id.btnRegisterInfo)

            val buildingName = buildingNameMap[reservation.buildingId] ?: ""
            tvBuildingRoom.text = "${reservation.buildingId}. $buildingName — no. ${reservation.roomId}"
            tvDateTime.text = "${reservation.date} • ${periodToTime(reservation.periodStart)} - ${periodToTime(reservation.periodEnd + 1)}"
            tvAttendees.text = "Attendees: ${reservation.attendees}"
            tvPurpose.text = "Purpose: ${reservation.purpose}"
            tvStatus.text = reservation.status.replaceFirstChar { it.uppercase() }

            resetCardToDefault(card)

            when (reservation.status) {

                "approved" -> {
                    tvStatus.setTextColor(Color.WHITE)
                    tvStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_green)
                    btnCancel.visibility = View.VISIBLE
                }

                "finished" -> {
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
                    tvStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_gray)
                    btnRegisterInfo.visibility = View.VISIBLE   // ✔ 정보 등록 가능
                }

                "expired" -> {
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
                    tvStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_gray)
                    btnRegisterInfo.visibility = View.GONE      // ❌ 등록 비활성
                    btnCancel.visibility = View.GONE            // 취소도 불가
                    setCardToCanceledState(card)               // 흐리게 처리
                }

                "canceled" -> {
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
                    tvStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_gray)
                    setCardToCanceledState(card)
                }
            }


            card.setOnClickListener {

                ReservationDetailDialogFS(
                    reservation = reservation,

                    onCancelClick = {
                        ConfirmCancelDialog {
                            updateStatus(reservation.id, "canceled")
                        }.show(childFragmentManager, "ConfirmCancelDialog")
                    },

                    onRegisterClick = {
                        // ✔ 등록 가능한 상태인지 한 번 더 검사
                        if (reservation.status == "finished") {
                            val dialog = RegisterInfoDialog { features ->
                                updateRoomFeatures(reservation.buildingId, reservation.roomId, features)
                            }
                            dialog.show(parentFragmentManager, "RegisterInfoDialog")
                        }
                    }

                ).show(childFragmentManager, "ReservationDetailDialogFS")
            }


            btnCancel.setOnClickListener { 
                ConfirmCancelDialog {
                    updateStatus(reservation.id, "canceled")
                }.show(parentFragmentManager, "ConfirmCancelDialog")
            }
            btnRegisterInfo.setOnClickListener {
                val dialog = RegisterInfoDialog { features ->
                    updateRoomFeatures(reservation.buildingId, reservation.roomId, features)
                }
                dialog.show(parentFragmentManager, "RegisterInfoDialog")
            }

            container.addView(card)
        }
    }

    private fun updateStatus(id: String, newStatus: String) {
        db.collection("reservations").document(id)
            .update("status", newStatus)
            .addOnSuccessListener { loadReservations() }
            .addOnFailureListener { Toast.makeText(requireContext(), "Failed to update status", Toast.LENGTH_SHORT).show() }
    }

    private fun updateRoomFeatures(buildingId: String, roomId: String, features: List<String>) {
        if (buildingId.isEmpty() || roomId.isEmpty()) {
            Toast.makeText(requireContext(), "Cannot register info: Invalid reservation data.", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("buildings").document(buildingId)
            .collection("rooms").document(roomId)
            .update("features", features)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Classroom info registered successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to register info: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun addEmptyMessage(container: LinearLayout) {
        val tv = TextView(requireContext()).apply {
            text = "No reservations found."
            setPadding(16, 32, 16, 32)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_dark))
        }
        container.addView(tv)
    }

    private fun periodToTime(period: Int): String {
        val hour = 8 + period
        return String.format("%02d:00", hour)
    }

    private fun resetCardToDefault(card: View) {
        card.isClickable = true
        card.findViewById<TextView>(R.id.tvBuildingRoom).setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        card.findViewById<TextView>(R.id.tvDateTime).setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_dark))
        card.findViewById<TextView>(R.id.tvAttendees).setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_dark))
        card.findViewById<TextView>(R.id.tvPurpose).setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_dark))

        card.findViewById<MaterialButton>(R.id.btnCancel).visibility = View.GONE
        card.findViewById<MaterialButton>(R.id.btnRegisterInfo).visibility = View.GONE
    }

    private fun setCardToCanceledState(card: View) {
        val grayColor = ContextCompat.getColor(requireContext(), R.color.gray)
        card.findViewById<TextView>(R.id.tvBuildingRoom).setTextColor(grayColor)
        card.findViewById<TextView>(R.id.tvDateTime).setTextColor(grayColor)
        card.findViewById<TextView>(R.id.tvAttendees).setTextColor(grayColor)
        card.findViewById<TextView>(R.id.tvPurpose).setTextColor(grayColor)

        card.findViewById<MaterialButton>(R.id.btnCancel).visibility = View.GONE
        card.findViewById<MaterialButton>(R.id.btnRegisterInfo).visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Firestore에서 불러오는 Reservation 모델 */
data class ReservationFS(
    val id: String = "",
    val buildingId: String = "",
    val roomId: String = "",
    val date: String = "",
    val day: String = "",
    val periodStart: Int = 0,
    val periodEnd: Int = 0,
    val attendees: Int = 0,
    val purpose: String = "",
    val status: String = ""
)
