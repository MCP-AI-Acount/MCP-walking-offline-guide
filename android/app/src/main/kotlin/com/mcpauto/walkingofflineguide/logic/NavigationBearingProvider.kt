package com.mcpauto.walkingofflineguide.logic

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs

/**
 * 내비 앱 표준 융합 — IndoorAtlas/Google Maps 패턴:
 * - 정지·저속: [TYPE_ROTATION_VECTOR] 나침반 heading (기기가 가리키는 방향)
 * - 이동 중: Location course/bearing (GPS 진행 방향)
 * - 중간 속도: 두 값 가중 블렌드
 *
 * @see android.hardware.Sensor#TYPE_ROTATION_VECTOR
 * @see android.location.Location#getBearing
 */
class NavigationBearingProvider(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var compassDeg: Float? = null
    private var gpsCourseDeg: Float? = null
    private var speedMps: Float = 0f
    private var smoothedDeg: Float? = null

    var onBearing: ((Float) -> Unit)? = null

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        compassDeg = null
        gpsCourseDeg = null
        speedMps = 0f
        smoothedDeg = null
    }

    fun updateFromLocation(loc: Location, prev: Location?) {
        speedMps = loc.speed.coerceAtLeast(0f)
        gpsCourseDeg = resolveGpsCourse(loc, prev)
        publishFused()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val displayRotation = (appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.rotation
        when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                remappedMatrix,
            )
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_MINUS_Y,
                SensorManager.AXIS_X,
                remappedMatrix,
            )
            else -> System.arraycopy(rotationMatrix, 0, remappedMatrix, 0, 9)
        }
        SensorManager.getOrientation(remappedMatrix, orientation)
        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (azimuth < 0f) azimuth += 360f
        compassDeg = azimuth
        publishFused()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun resolveGpsCourse(loc: Location, prev: Location?): Float? {
        if (loc.hasBearing() && !loc.bearing.isNaN() && loc.speed >= GPS_COURSE_MIN_SPEED) {
            return normalize(loc.bearing)
        }
        val p = prev ?: return gpsCourseDeg
        if (p.distanceTo(loc) >= GPS_MIN_STEP_M) {
            return normalize(p.bearingTo(loc))
        }
        return gpsCourseDeg
    }

    /** speed 기반 heading↔bearing 블렌드 (Google Maps / IndoorAtlas 문서) */
    private fun fuse(): Float? {
        val compass = compassDeg
        val gps = gpsCourseDeg
        return when {
            compass == null && gps == null -> null
            compass == null -> gps
            gps == null -> compass
            speedMps >= GPS_DOMINANT_SPEED -> gps
            speedMps <= COMPASS_DOMINANT_SPEED -> compass
            else -> {
                val w = ((speedMps - COMPASS_DOMINANT_SPEED) /
                    (GPS_DOMINANT_SPEED - COMPASS_DOMINANT_SPEED)).coerceIn(0f, 1f)
                lerpAngle(compass, gps, w)
            }
        }
    }

    private fun publishFused() {
        val fused = fuse() ?: return
        val alpha = if (speedMps >= GPS_DOMINANT_SPEED) 0.72f else 0.58f
        smoothedDeg = smoothAngle(smoothedDeg, fused, alpha)
        onBearing?.invoke(smoothedDeg!!)
    }

    private fun normalize(deg: Float): Float {
        var d = deg % 360f
        if (d < 0f) d += 360f
        return d
    }

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var diff = b - a
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        return normalize(a + diff * t)
    }

    private fun smoothAngle(prev: Float?, next: Float, alpha: Float): Float {
        if (prev == null) return next
        var diff = next - prev
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        if (abs(diff) < 0.3f) return prev
        return normalize(prev + diff * alpha)
    }

    companion object {
        /** ~1.8km/h 미만 — 나침반(heading) 우선 */
        private const val COMPASS_DOMINANT_SPEED = 0.5f
        /** ~7km/h 이상 — GPS course(bearing) 우선 */
        private const val GPS_DOMINANT_SPEED = 2.0f
        private const val GPS_COURSE_MIN_SPEED = 0.4f
        private const val GPS_MIN_STEP_M = 2f
    }
}
