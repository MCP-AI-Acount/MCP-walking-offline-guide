package com.mcpauto.walkingofflineguide.logic

/**
 * 헤딩업 회전 중 GPS 미세 흔들림으로 지도 축이 움직이는 것 방지.
 * 정지·저속에서는 anchor 고정, 실제 이동(속도·거리)일 때만 갱신.
 */
class NavigationAnchor {
    var lat: Double = 0.0
        private set
    var lon: Double = 0.0
        private set

    private var ready = false

    val isReady: Boolean get() = ready

    fun reset(lat: Double, lon: Double) {
        this.lat = lat
        this.lon = lon
        ready = true
    }

    /** @return anchor 좌표가 바뀌었으면 true */
    fun update(lat: Double, lon: Double, speedMps: Float): Boolean {
        if (!ready) {
            reset(lat, lon)
            return true
        }
        val distM = PoiLogic.haversineM(lat, lon, this.lat, this.lon)
        if (speedMps >= MOVE_SPEED_MPS || distM >= MOVE_DISTANCE_M) {
            this.lat = lat
            this.lon = lon
            return true
        }
        return false
    }

    companion object {
        private const val MOVE_SPEED_MPS = 0.35f
        private const val MOVE_DISTANCE_M = 2.5
    }
}
