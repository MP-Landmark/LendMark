package com.example.lendmark.ui.reservation

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.lendmark.R
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.MapViewInfo
import com.kakao.vectormap.MapType

class ReservationMapFragment : Fragment() {

    private var mapView: MapView? = null
    private var kakaoMap: KakaoMap? = null

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
                }

                override fun getPosition(): LatLng =
                    LatLng.from(37.6316, 127.0775)   // 서울과기대 근처

                override fun getZoomLevel(): Int = 16

                override fun isVisible(): Boolean = true

                // ✅ RenderView 오류 방지: 공식 권장값
                override fun getMapViewInfo(): MapViewInfo =
                    MapViewInfo.from("openmap", MapType.NORMAL)

                // ⚠ getViewName(), getTag()는 아예 override 안 함
            }
        )
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
        super.onDestroyView()
    }
}
