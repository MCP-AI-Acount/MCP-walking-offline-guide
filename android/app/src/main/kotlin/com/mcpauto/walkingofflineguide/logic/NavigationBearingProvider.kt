package com.mcpauto.walkingofflineguide.logic

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import kotlin.math.abs

/**
 * 헤딩업 bearing — [MapHeading] + (옵션) GPS course.
 */
class NavigationBearingProvider(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val rotationMatrix = FloatArray(9)

    private var compassDeg: Float? = null
    private var gpsCourseDeg: Float? = null
    private var speedMps: Float = 0f
    private var smoothedDeg: Float? = null

    var compassOnly: Boolean = true
    var onBearing: ((Float) -> Unit)? = null

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME,
                mainHandler,
            )
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
        if (compassOnly) return
        speedMps = loc.speed.coerceAtLeast(0f)
        gpsCourseDeg = resolveGpsCourse(loc, prev)
        publishFused()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,
            -> Unit
            else -> return
        }
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val bearing = MapHeading.landscapeForwardBearingDeg(
            rotationMatrix,
            smoothedDeg ?: compassDeg,
        )
        if (bearing != null) {
            compassDeg = bearing
            publishFused()
        }
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

    private fun fuse(): Float? {
        val compass = compassDeg
        if (compassOnly) return compass
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
        val out = if (compassOnly) {
            fused
        } else {
            smoothAngleLimited(smoothedDeg, fused, SENSOR_SMOOTH_ALPHA, MAX_STEP_DEG)
                .also { smoothedDeg = it }
        }
        if (compassOnly) {
            smoothedDeg = out
        }
        val cb = onBearing ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cb(out)
        } else {
            mainHandler.post { cb(out) }
        }
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

    private fun smoothAngleLimited(prev: Float?, next: Float, alpha: Float, maxStepDeg: Float): Float {
        if (prev == null) return next
        var diff = next - prev
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        if (abs(diff) < 0.05f) return prev
        val step = (diff * alpha).coerceIn(-maxStepDeg, maxStepDeg)
        return normalize(prev + step)
    }

    companion object {
        private const val COMPASS_DOMINANT_SPEED = 0.5f
        private const val GPS_DOMINANT_SPEED = 2.0f
        private const val GPS_COURSE_MIN_SPEED = 0.4f
        private const val GPS_MIN_STEP_M = 2f
        private const val SENSOR_SMOOTH_ALPHA = 0.72f
        private const val MAX_STEP_DEG = 12f
    }
}
