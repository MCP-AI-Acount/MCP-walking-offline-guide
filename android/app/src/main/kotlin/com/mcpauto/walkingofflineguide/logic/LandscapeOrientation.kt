package com.mcpauto.walkingofflineguide.logic

import android.app.Activity
import android.view.Surface

/**
 * 앱 = **가로 고정** (sensorLandscape).
 * 센서 remap·bearing은 여기서 고정된 landscape 회전값만 사용.
 */
object LandscapeOrientation {
    /** [Surface.ROTATION_90] 또는 [Surface.ROTATION_270] */
    var displayRotation: Int = Surface.ROTATION_90
        private set

    val isLockedLandscape: Boolean get() = true

    fun init(activity: Activity) {
        val rot = activity.display?.rotation ?: Surface.ROTATION_90
        displayRotation = when (rot) {
            Surface.ROTATION_270 -> Surface.ROTATION_270
            else -> Surface.ROTATION_90
        }
    }
}
