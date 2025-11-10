package com.example.lendmark.data.repository

import com.example.lendmark.data.model.Building
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class BuildingRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun getBuildings(): List<Building> {
        val snapshot = db.collection("buildings").get().await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            Building(
                name = data["name"] as? String ?: "",
                code = (data["code"] as? Long)?.toInt() ?: 0,
                naverMapLat = data["naverMapLat"] as? Double ?: 0.0,
                naverMapLng = data["naverMapLng"] as? Double ?: 0.0,
                roomCount = (doc.reference.collection("rooms").get().await().size()),
                imageUrl = data["imageUrl"] as? String ?: ""
            )
        }
    }
}
