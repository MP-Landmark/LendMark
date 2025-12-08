package com.example.lendmark.ui.my

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.lendmark.R
import com.example.lendmark.databinding.FragmentMyReservationBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class MyReservationFragment : Fragment() {

    private var _binding: FragmentMyReservationBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

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

    // ----------------------------------------------------------------------
    // 1) Load reservation + building name
    // ----------------------------------------------------------------------
    private fun loadReservations() {
        if (uid == null) return

        db.collection("buildings").get()
            .addOnSuccessListener { buildingsSnapshot ->
                if (!isAdded) return@addOnSuccessListener

                buildingNameMap = buildingsSnapshot.documents.associate {
                    it.id to (it.getString("name") ?: "")
                }

                db.collection("reservations")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (!isAdded) return@addOnSuccessListener
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
                        }
                            // 정렬 로직 개선
                            // 1순위: 상태 (Approved가 무조건 위, 나머지는 아래)
                            // 2순위: Approved 내부에서는 날짜/시간이 빠른 순서 (오름차순: 15시 -> 16시)
                            // 3순위: Finished 내부에서는 날짜/시간이 느린 순서 (내림차순: 최근 기록이 위로)
                            .sortedWith(Comparator { r1, r2 ->
                                val isR1Approved = r1.status == "approved"
                                val isR2Approved = r2.status == "approved"

                                // 1. 상태 비교 (Approved 우선)
                                if (isR1Approved && !isR2Approved) return@Comparator -1
                                if (!isR1Approved && isR2Approved) return@Comparator 1

                                // 2. 같은 그룹 내 정렬
                                if (isR1Approved) {
                                    // 둘 다 승인됨 -> 임박한 순서대로 (오름차순)
                                    val dateCompare = r1.date.compareTo(r2.date)
                                    if (dateCompare != 0) dateCompare else r1.periodStart - r2.periodStart
                                } else {
                                    // 둘 다 완료됨 -> 최신 기록이 위로 (내림차순)
                                    val dateCompare = r2.date.compareTo(r1.date)
                                    if (dateCompare != 0) dateCompare else r2.periodStart - r1.periodStart
                                }
                            })

                        displayReservations()
                    }
            }
    }

    // ----------------------------------------------------------------------
    // 2) Display reservation list
    // ----------------------------------------------------------------------
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
                            it.status == "reviewed" ||
                            it.status == "canceled" ||
                            it.status == "expired"
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

            // Show reservation detail dialog on card click
            card.setOnClickListener {
                val dialog = ReservationDetailDialogFS(
                    reservation = reservation,
                    onCancelClick = { reservationId -> showCancelConfirmationDialog(reservationId) },
                    onRegisterClick = { showRegisterInfoDialog(reservation) }
                )
                dialog.show(parentFragmentManager, "ReservationDetailDialog")
            }

            when (reservation.status) {
                "approved" -> {
                    tvStatus.setTextColor(Color.WHITE)
                    tvStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_green)
                    btnCancel.visibility = View.VISIBLE
                    btnCancel.setOnClickListener {
                        showCancelConfirmationDialog(reservation.id)
                    }
                }

                "finished" -> {
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
                    tvStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_gray)
                    btnRegisterInfo.visibility = View.VISIBLE
                }
                "reviewed" -> {
                    tvStatus.text = "등록완료"
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
                    tvStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_gray)

                    btnRegisterInfo.visibility = View.GONE
                    btnCancel.visibility = View.GONE

                    setCardToReviewedState(card)  // 회색화 (클릭은 가능)
                }

                "expired", "canceled" -> {
                    tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
                    tvStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_gray)
                    setCardToCanceledState(card)
                }
            }

            // 리스트 상의 리뷰 작성 버튼 동작
            btnRegisterInfo.setOnClickListener {
                showRegisterInfoDialog(reservation)
            }

            container.addView(card)
        }
    }

    private fun showRegisterInfoDialog(reservation: ReservationFS) {
        val dialog = RegisterInfoDialog { capacity, classType, tags, imageUris ->
            uploadReviewToFirestore(
                reservation = reservation,
                capacity = capacity,
                classType = classType,
                tags = tags,
                imageUris = imageUris
            )
        }
        dialog.show(parentFragmentManager, "RegisterInfoDialog")
    }

    private fun showCancelConfirmationDialog(reservationId: String) {
        val dialog = ConfirmCancelDialog {
            cancelReservation(reservationId)
        }
        dialog.show(parentFragmentManager, "ConfirmCancelDialog")
    }

    private fun cancelReservation(reservationId: String) {
        db.collection("reservations").document(reservationId)
            .update("status", "canceled")
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                Toast.makeText(requireContext(), "Reservation Canceled", Toast.LENGTH_SHORT).show()
                loadReservations() // Refresh list
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setCardToReviewedState(card: View) {
        val grayColor = ContextCompat.getColor(requireContext(), R.color.gray)

        // [수정됨] card.isClickable = false 삭제함 -> 이제 클릭 가능
        card.alpha = 0.5f   // 전체 흐리게

        card.findViewById<TextView>(R.id.tvBuildingRoom).setTextColor(grayColor)
        card.findViewById<TextView>(R.id.tvDateTime).setTextColor(grayColor)
        card.findViewById<TextView>(R.id.tvAttendees).setTextColor(grayColor)
        card.findViewById<TextView>(R.id.tvPurpose).setTextColor(grayColor)

        card.findViewById<MaterialButton>(R.id.btnCancel).visibility = View.GONE
        card.findViewById<MaterialButton>(R.id.btnRegisterInfo).visibility = View.GONE
    }


    // ----------------------------------------------------------------------
    // 3) Upload review to Firestore + Storage
    // ----------------------------------------------------------------------
    private fun uploadReviewToFirestore(
        reservation: ReservationFS,
        capacity: Int,
        classType: String,
        tags: List<String>,
        imageUris: List<Uri>
    ) {
        val reviewDoc = db.collection("reviews").document()

        val reviewData = hashMapOf(
            "reservationId" to reservation.id,
            "buildingId" to reservation.buildingId,
            "buildingName" to buildingNameMap[reservation.buildingId],
            "roomId" to reservation.roomId,
            "roomName" to "${reservation.roomId}호",
            "userId" to uid,
            "capacity" to capacity,
            "classType" to classType,
            "tags" to tags,
            "imageUrls" to listOf<String>(),
            "createdAt" to System.currentTimeMillis()
        )

        reviewDoc.set(reviewData)
            .addOnSuccessListener {

                if (imageUris.isEmpty()) {
                    db.collection("reservations")
                        .document(reservation.id)
                        .update("status", "reviewed")
                        .addOnSuccessListener {
                            loadReservations()
                        }
                    Toast.makeText(requireContext(), "리뷰가 저장되었습니다!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                uploadImages(reservation.id, reviewDoc.id, imageUris) { urls ->
                    reviewDoc.update("imageUrls", urls)
                    db.collection("reservations")
                        .document(reservation.id)
                        .update("status", "reviewed")
                        .addOnSuccessListener {
                            loadReservations()
                        }
                    Toast.makeText(requireContext(), "리뷰가 저장되었습니다!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "리뷰 저장 실패", Toast.LENGTH_SHORT).show()
            }
    }


    // ----------------------------------------------------------------------
    // 4) Upload images to Firebase Storage
    // ----------------------------------------------------------------------
    private fun uploadImages(
        reservationId: String,
        reviewId: String,
        uriList: List<Uri>,
        onComplete: (List<String>) -> Unit
    ) {
        val storageRef = FirebaseStorage.getInstance().reference
        val uploadedUrls = mutableListOf<String>()
        var uploadedCount = 0

        uriList.forEachIndexed { index, uri ->
            val fileRef = storageRef.child("reviews/$reservationId/${reviewId}_img_$index.jpg")

            fileRef.putFile(uri)
                .addOnSuccessListener {
                    fileRef.downloadUrl.addOnSuccessListener { downloadUri ->

                        uploadedUrls.add(downloadUri.toString())
                        uploadedCount++

                        if (uploadedCount == uriList.size) {
                            onComplete(uploadedUrls)
                        }
                    }
                }
        }
    }

    // ----------------------------------------------------------------------
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
        card.findViewById<TextView>(R.id.tvBuildingRoom)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        card.findViewById<TextView>(R.id.tvDateTime)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_dark))
        card.findViewById<TextView>(R.id.tvAttendees)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_dark))
        card.findViewById<TextView>(R.id.tvPurpose)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_dark))

        card.findViewById<MaterialButton>(R.id.btnCancel).visibility = View.GONE
        card.findViewById<MaterialButton>(R.id.btnRegisterInfo).visibility = View.GONE
    }

    private fun setCardToCanceledState(card: View) {
        val gray = ContextCompat.getColor(requireContext(), R.color.gray)
        card.findViewById<TextView>(R.id.tvBuildingRoom).setTextColor(gray)
        card.findViewById<TextView>(R.id.tvDateTime).setTextColor(gray)
        card.findViewById<TextView>(R.id.tvAttendees).setTextColor(gray)
        card.findViewById<TextView>(R.id.tvPurpose).setTextColor(gray)
        card.findViewById<MaterialButton>(R.id.btnCancel).visibility = View.GONE
        card.findViewById<MaterialButton>(R.id.btnRegisterInfo).visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ---------------------------------------------------------------
// Reservation Firestore Model
// ---------------------------------------------------------------
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