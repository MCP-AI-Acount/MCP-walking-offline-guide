package com.mcpauto.walkingofflineguide.logic

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.mcpauto.walkingofflineguide.data.UserPosition
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class LocationHelper(context: Context) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)
    private var callback: LocationCallback? = null
    private var lastFix: Location? = null
    private var bearingProvider: NavigationBearingProvider? = null

    @SuppressLint("MissingPermission")
    fun startUpdates(
        onUpdate: (UserPosition) -> Unit,
        bearingProvider: NavigationBearingProvider? = null,
        navigationLock: Boolean = false,
    ) {
        stopUpdates()
        this.bearingProvider = bearingProvider
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, if (navigationLock) 400L else 200L)
            .setMinUpdateIntervalMillis(if (navigationLock) 300L else 100L)
            .setMinUpdateDistanceMeters(if (navigationLock) 2f else 1f)
            .setMaxUpdateDelayMillis(500L)
            .setWaitForAccurateLocation(false)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val prev = lastFix
                bearingProvider?.updateFromLocation(loc, prev)
                lastFix = Location(loc)
                onUpdate(
                    UserPosition(
                        loc.latitude,
                        loc.longitude,
                        bearingDeg = null,
                        speedMps = loc.speed.coerceAtLeast(0f),
                    ),
                )
            }
        }
        callback = cb
        client.requestLocationUpdates(request, cb, Looper.getMainLooper())
    }

    fun stopUpdates() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        lastFix = null
        bearingProvider = null
    }

    @SuppressLint("MissingPermission")
    suspend fun acquirePosition(): UserPosition = suspendCancellableCoroutine { cont ->
        client.lastLocation
            .addOnSuccessListener { last ->
                val stale = last == null || last.elapsedRealtimeAgeMillis > 60_000 ||
                    (last.hasAccuracy() && last.accuracy > 400f)
                if (!stale && cont.isActive) {
                    cont.resume(toUserPosition(last))
                    return@addOnSuccessListener
                }
                val token = CancellationTokenSource()
                cont.invokeOnCancellation { token.cancel() }
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                    .addOnSuccessListener { loc ->
                        when {
                            loc != null && cont.isActive -> cont.resume(toUserPosition(loc))
                            last != null && cont.isActive -> cont.resume(toUserPosition(last))
                            cont.isActive -> cont.resumeWithException(
                                IllegalStateException("위치 수신 실패 — GPS·WiFi 위치를 켠 뒤 다시 시도해 주세요."),
                            )
                        }
                    }
                    .addOnFailureListener { e ->
                        if (last != null && cont.isActive) {
                            cont.resume(toUserPosition(last))
                        } else if (cont.isActive) {
                            cont.resumeWithException(e)
                        }
                    }
            }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resumeWithException(e)
            }
    }

    private fun toUserPosition(loc: Location): UserPosition =
        UserPosition(loc.latitude, loc.longitude, speedMps = loc.speed.coerceAtLeast(0f))
}
