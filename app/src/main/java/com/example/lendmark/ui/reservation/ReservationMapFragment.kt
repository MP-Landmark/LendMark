// app/src/main/java/com/example/lendmark/ui/reservation/ReservationMapFragment.kt

package com.example.lendmark.ui.reservation

import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.lendmark.R
import com.example.lendmark.data.model.Building
import com.example.lendmark.ui.main.MainActivity
import com.example.lendmark.ui.room.RoomListFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.kakao.vectormap.*

import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles

class ReservationMapFragment : Fragment() {

    private var mapView: MapView? = null
    private var kakaoMap: KakaoMap? = null
    private val db = FirebaseFirestore.getInstance()

    // 라벨(마커)을 올릴 레이어
    private var buildingLayer: LabelLayer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_reservation_map, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView = view.findViewById(R.id.map_view)

        mapView?.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {
                    Log.d("KAKAO_MAP", "onMapDestroy")
                }

                override fun onMapError(error: Exception) {
                    Log.e("KAKAO_MAP", "Map error: ${error.message}", error)
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
                    Log.d("KAKAO_MAP", "onMapReady OK")

                    val labelManager = map.labelManager
                    if (labelManager == null) {
                        Log.e("KAKAO_MAP", "labelManager is null")
                        return
                    }

                    // 기본 라벨 레이어 사용
                    buildingLayer = labelManager.layer
                    buildingLayer?.setVisible(true)
                    buildingLayer?.setClickable(true)

                    // 마커(라벨) 클릭 리스너
                    map.setOnLabelClickListener(object : KakaoMap.OnLabelClickListener {
                        override fun onLabelClicked(
                            kakaoMap: KakaoMap,
                            layer: LabelLayer,
                            label: Label
                        ): Boolean {
                            if (layer !== buildingLayer) return false

                            val building = label.tag as? Building ?: return false

                            val bundle = Bundle().apply {
                                putString("buildingId", building.code.toString())
                                putString("buildingName", building.name)
                            }

                            (requireActivity() as MainActivity).replaceFragment(
                                RoomListFragment().apply { arguments = bundle }
                            )
                            return true
                        }
                    })

                    // Firestore → 건물 목록 불러와서 마커 찍기
                    loadBuildingsAndAddMarkers()
                }

                override fun getPosition(): LatLng =
                    LatLng.from(37.632632, 127.078056)   // 다산관 근처

                override fun getZoomLevel(): Int = 16

                override fun isVisible(): Boolean = true

                override fun getMapViewInfo(): MapViewInfo =
                    MapViewInfo.from("openmap", MapType.NORMAL)
            }
        )
    }

    /**
     * Firestore에서 buildings 컬렉션을 읽어와
     * 각 건물 위치에 커스텀 마커를 추가한다.
     */
    private fun loadBuildingsAndAddMarkers() {
        db.collection("buildings")
            .orderBy("code")
            .get()
            .addOnSuccessListener { result ->
                Log.d("KAKAO_MAP", "buildings loaded: ${result.size()}")

                for (doc in result) {
                    val building = doc.toObject(Building::class.java)
                    building.id = doc.id

                    Log.d(
                        "KAKAO_MAP",
                        "building ${building.name} lat=${building.naverMapLat}, lng=${building.naverMapLng}"
                    )

                    if (building.naverMapLat != 0.0 && building.naverMapLng != 0.0) {
                        val pos = LatLng.from(
                            building.naverMapLat,
                            building.naverMapLng
                        )
                        addMarkerForBuilding(building, pos)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("KAKAO_MAP", "Failed to load buildings: ${e.message}", e)
            }
    }

    /**
     * 하나의 건물에 대해 예약률 마커 비트맵을 만들고, 지도에 라벨로 추가
     */
    private fun addMarkerForBuilding(building: Building, position: LatLng) {
        val map = kakaoMap ?: return
        val labelManager = map.labelManager ?: return
        val layer = buildingLayer ?: labelManager.layer ?: return

        // TODO: 여기서 실제 예약률 계산 결과로 바꿔주면 됨
        val occupancyPercent = 50  // 임시 값 (0~100)

        // 1) 커스텀 마커 비트맵 생성
        val markerBitmap = createBuildingMarkerBitmap(occupancyPercent)

        // 2) 비트맵으로 LabelStyle 등록
        val styles: LabelStyles = labelManager.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from(markerBitmap)
            )
        ) ?: run {
            Log.e("KAKAO_MAP", "LabelStyles is null")
            return
        }

        // 3) LabelOptions 생성 후 라벨 추가
        val options = LabelOptions.from(position)
            .setStyles(styles)
            .setTag(building)
            .setClickable(true)

        val label = layer.addLabel(options)
        Log.d(
            "KAKAO_MAP",
            "label added for ${building.name}, percent=$occupancyPercent, label=$label"
        )
    }

    /**
     * 예약률(percent)에 따라 색이 달라지는 동그라미 + 텍스트 마커 비트맵 생성
     * (네가 올려준 33%, 65%, 88% 같은 스타일의 원형 뱃지)
     */
    private fun createBuildingMarkerBitmap(percent: Int): Bitmap {
        val size = 120   // 전체 비트맵 크기(px) – 필요하면 조절
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 배경 원 색상 (예약률 구간별)
        val bgColor = when {
            percent < 40 -> Color.parseColor("#4CAF50")   // green (~40% 여유)
            percent < 60 -> Color.parseColor("#FFC107")   // yellow (40~60 보통)
            percent < 80 -> Color.parseColor("#FF9800")   // orange (60~80 혼잡)
            else -> Color.parseColor("#F44336")           // red (80%+ 매우 혼잡)
        }

        // 바깥 테두리 (네이비 같은 거)
//        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//            color = Color.parseColor("#202040")
//            style = Paint.Style.FILL
//        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }

        val cx = size / 2f
        val cy = size / 2f
        //val outerR = size / 2f
        val innerR = size / 2.4f

        // 바깥 원 + 안쪽 색 원
        //canvas.drawCircle(cx, cy, outerR, outerPaint)
        canvas.drawCircle(cx, cy, innerR, innerPaint)

        // 텍스트 ("33%")
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 35f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val text = "$percent%"
        val yPos = cy - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, cx, yPos, textPaint)

        return bitmap
    }

    override fun onResume() {
        super.onResume()
        mapView?.resume()
    }

    override fun onPause() {
        mapView?.pause()
        super.onPause()
    }

    override fun onDestroyView() {
        mapView = null
        kakaoMap = null
        buildingLayer = null
        super.onDestroyView()
    }
}
