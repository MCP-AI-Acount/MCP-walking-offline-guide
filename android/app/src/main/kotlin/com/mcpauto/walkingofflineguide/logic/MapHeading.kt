package com.mcpauto.walkingofflineguide.logic

import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.abs

/**
 * 세로(portrait) 2D 헤딩 — 수평면 azimuth만 사용.
 * 지도는 Z축 회전만(기울임 없음). 폰이 거의 세로일 때만 마지막 bearing 유지.
 */
object MapHeading {
    /** 폰 수직(엘리베이터·지하 등) — azimuth 신뢰↓ → 마지막 값 유지 */
    private const val VERTICAL_HOLD_PITCH_DEG = 62f

    private val orientation = FloatArray(3)

    fun remapPortrait(inR: FloatArray, outR: FloatArray): Boolean =
        when (PortraitOrientation.displayRotation) {
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                inR, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, outR,
            )
            else -> SensorManager.remapCoordinateSystem(
                inR, SensorManager.AXIS_X, SensorManager.AXIS_Y, outR,
            )
        }

    /**
     * @param lastBearingDeg 기울기 과다·센서 공백 시 유지할 마지막 yaw(0~360)
     */
    fun portraitForwardBearingDeg(rotationMatrix: FloatArray, lastBearingDeg: Float? = null): Float? {
        val yaw = gravityAxisYawDeg(rotationMatrix) ?: return lastBearingDeg
        val offset = when (PortraitOrientation.displayRotation) {
            Surface.ROTATION_180 -> 180f
            else -> 0f
        }
        return normalizeDeg(yaw + offset)
    }

    /** 지도 회전각 — 전방 헤딩의 역방향(북쪽 위 타일 → 화면 위=전방) */
    fun mapRotationDeg(forwardHeadingDeg: Float): Float = normalizeDeg(-forwardHeadingDeg)

    /**
     * 수평면 투영 yaw(azimuth)만 — 기울어도 지도는 Z(중력)축으로만 360° 회전.
     */
    private fun gravityAxisYawDeg(rotationMatrix: FloatArray): Float? {
        val remapped = FloatArray(9)
        if (!remapPortrait(rotationMatrix, remapped)) {
            System.arraycopy(rotationMatrix, 0, remapped, 0, 9)
        }
        SensorManager.getOrientation(remapped, orientation)
        val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
        if (abs(pitchDeg) > VERTICAL_HOLD_PITCH_DEG) {
            return null
        }
        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (azimuth < 0f) azimuth += 360f
        return azimuth
    }

    private fun normalizeDeg(deg: Float): Float {
        var d = deg % 360f
        if (d < 0f) d += 360f
        return d
    }
}
