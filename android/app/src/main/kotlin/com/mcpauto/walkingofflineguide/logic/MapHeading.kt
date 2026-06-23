package com.mcpauto.walkingofflineguide.logic

import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.abs

/**
 * 가로(sensorLandscape) 헤딩 — AOSP remap + getOrientation.
 * 지도 회전 = **중력(하늘) 축 yaw만** — pitch/roll은 무시.
 * 전방=화면 위: azimuth −90° (ROTATION_270은 +90°).
 */
object MapHeading {
    /** pitch/roll 과다 시 yaw 고정(마지막 값 유지) — 지도 기울임 금지 */
    private const val EXTREME_TILT_DEG = 78f

    private val orientation = FloatArray(3)

    fun remapLandscape(inR: FloatArray, outR: FloatArray): Boolean =
        when (LandscapeOrientation.displayRotation) {
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                inR, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, outR,
            )
            else -> SensorManager.remapCoordinateSystem(
                inR, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, outR,
            )
        }

    /**
     * @param lastBearingDeg 기울기 과다·센서 공백 시 유지할 마지막 yaw(0~360)
     */
    fun landscapeForwardBearingDeg(rotationMatrix: FloatArray, lastBearingDeg: Float? = null): Float? {
        val yaw = gravityAxisYawDeg(rotationMatrix) ?: return lastBearingDeg
        val offset = when (LandscapeOrientation.displayRotation) {
            Surface.ROTATION_270 -> 90f
            else -> -90f
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
        if (!remapLandscape(rotationMatrix, remapped)) {
            System.arraycopy(rotationMatrix, 0, remapped, 0, 9)
        }
        SensorManager.getOrientation(remapped, orientation)
        val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
        if (abs(pitchDeg) > EXTREME_TILT_DEG && abs(rollDeg) > EXTREME_TILT_DEG) {
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
