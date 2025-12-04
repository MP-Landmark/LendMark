package com.example.lendmark.ui.notification

import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.lendmark.databinding.DialogNotificationDetailBinding
import com.example.lendmark.ui.my.ConfirmCancelDialog
import com.example.lendmark.ui.my.RegisterInfoDialog
import com.example.lendmark.ui.my.ReservationDetailDialogFS
import com.example.lendmark.ui.my.ReservationFS
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source // [중요] 이 import가 있어야 서버 강제 조회가 가능합니다
import com.google.firebase.storage.FirebaseStorage

class NotificationDetailDialog(
    private val item: NotificationItem
) : DialogFragment() {

    private val db = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    private var _binding: DialogNotificationDetailBinding? = null
    private val binding get() = _binding!!

    // 이미 조회된 예약 정보를 저장 (중복 호출 방지)
    private var fetchedReservation: ReservationFS? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogNotificationDetailBinding.inflate(LayoutInflater.from(context))

        binding.tvTitle.text = item.title
        binding.tvDetail.text = "Reservation at: ${item.location} (${item.startTime} - ${item.endTime})"

        // 1. 처음엔 버튼을 '확인 중...' 상태로 잠금
        binding.btnGoReservation.isEnabled = false
        binding.btnGoReservation.alpha = 0.5f
        binding.btnGoReservation.text = "Checking..."

        // 3. 버튼 클릭 시 (이미 조회된 데이터가 있을 때만 작동)
        binding.btnGoReservation.setOnClickListener {
            fetchedReservation?.let { reservation ->
                openReservationDetail(reservation)
            }
        }

        binding.btnConfirm.setOnClickListener {
            dismiss()
        }

        // 2. 다이얼로그가 뜨자마자 서버 상태 강제 확인 시작
        checkStatusAutomatically()

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun checkStatusAutomatically() {
        val safeContext = context ?: return

        // [Source.SERVER] : "캐시 믿지 말고 무조건 서버 갔다 와!" (취소 반영 안 되는 문제 해결의 핵심)
        db.collection("reservations").document(item.reservationId)
            .get(Source.SERVER)
            .addOnSuccessListener { doc ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                if (!doc.exists()) {
                    Toast.makeText(safeContext, "Reservation no longer exists.", Toast.LENGTH_SHORT).show()
                    dismiss()
                    return@addOnSuccessListener
                }

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

                // ─────────────────────────────────────────────────────────────
                // 서버에서 막 가져온 따끈따끈한 데이터가 'canceled'라면?
                // ─────────────────────────────────────────────────────────────
                if (reservation.status == "canceled") {
                    // 사용자에게 버튼 누를 기회도 주지 않고 바로 닫아버림
                    Toast.makeText(safeContext, "This reservation has been canceled.", Toast.LENGTH_SHORT).show()
                    dismiss()
                    return@addOnSuccessListener
                }

                // 취소 안 된 예약이면 -> 데이터 저장 후 버튼 활성화
                fetchedReservation = reservation

                binding.btnGoReservation.isEnabled = true
                binding.btnGoReservation.alpha = 1.0f
                binding.btnGoReservation.text = "Go to Reservation Details"
            }
            .addOnFailureListener {
                // 인터넷 연결 문제 등으로 서버 조회 실패 시
                if (isAdded && _binding != null) {
                    binding.btnGoReservation.text = "Connection Failed"
                    Toast.makeText(safeContext, "상태 확인 실패. 인터넷을 확인하세요.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun openReservationDetail(reservation: ReservationFS) {
        val safeFragmentManager = parentFragmentManager
        val safeContext = requireContext()

        // 시작 알림인지 확인
        val isStartNotification = item.title.contains("Start", ignoreCase = true) ||
                item.title.contains("시작", ignoreCase = true) ||
                item.title.contains("Upcoming", ignoreCase = true)

        val statusForDialog = if (!isStartNotification && reservation.status == "approved") {
            "view_only"
        } else {
            reservation.status
        }

        val reservationForDisplay = reservation.copy(status = statusForDialog)

        val dialog = ReservationDetailDialogFS(
            reservation = reservationForDisplay,
            onCancelClick = { resId ->
                showCancelConfirmationDialog(safeFragmentManager, safeContext, resId)
            },
            onRegisterClick = {
                showRegisterInfoDialog(safeFragmentManager, safeContext, reservation)
            }
        )
        dialog.show(safeFragmentManager, "ReservationDetailDialogFS")

        dismiss() // 현재 알림창 닫기
    }

    // ─────────────────────────────────────────────────────────────
    //  Context 안전하게 사용하는 함수들
    // ─────────────────────────────────────────────────────────────

    private fun showCancelConfirmationDialog(fm: FragmentManager, context: Context, reservationId: String) {
        val dialog = ConfirmCancelDialog {
            cancelReservation(context, reservationId)
        }
        dialog.show(fm, "ConfirmCancelDialog")
    }

    private fun cancelReservation(context: Context, reservationId: String) {
        db.collection("reservations").document(reservationId)
            .update("status", "canceled")
            .addOnSuccessListener {
                Toast.makeText(context, "Reservation Canceled", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRegisterInfoDialog(fm: FragmentManager, context: Context, reservation: ReservationFS) {
        val dialog = RegisterInfoDialog { capacity, classType, tags, imageUris ->
            uploadReviewToFirestore(context, reservation, capacity, classType, tags, imageUris)
        }
        dialog.show(fm, "RegisterInfoDialog")
    }

    private fun uploadReviewToFirestore(
        context: Context,
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
            "buildingName" to "",
            "roomId" to reservation.roomId,
            "roomName" to "${reservation.roomId}호",
            "userId" to uid,
            "capacity" to capacity,
            "classType" to classType,
            "tags" to tags,
            "imageUrls" to listOf<String>(),
            "createdAt" to System.currentTimeMillis()
        )

        reviewDoc.set(reviewData).addOnSuccessListener {
            if (imageUris.isEmpty()) {
                finalizeReview(context, reservation.id)
            } else {
                uploadImages(reservation.id, reviewDoc.id, imageUris) { urls ->
                    reviewDoc.update("imageUrls", urls)
                    finalizeReview(context, reservation.id)
                }
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to save review", Toast.LENGTH_SHORT).show()
        }
    }

    private fun finalizeReview(context: Context, reservationId: String) {
        db.collection("reservations").document(reservationId)
            .update("status", "reviewed")
            .addOnSuccessListener {
                Toast.makeText(context, "Review saved!", Toast.LENGTH_SHORT).show()
            }
    }

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
            fileRef.putFile(uri).addOnSuccessListener {
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
}