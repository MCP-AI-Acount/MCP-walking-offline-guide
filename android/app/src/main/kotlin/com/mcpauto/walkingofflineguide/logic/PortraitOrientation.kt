package com.mcpauto.walkingofflineguide.logic

import android.app.Activity
import android.view.Surface

/**
 * 앱 = **세로 고정** (portrait).
 * 센서 remap·bearing은 여기서 고정된 portrait 회전값만 사용.
 */
object PortraitOrientation {
    /** [Surface.ROTATION_0] 또는 [Surface.ROTATION_180] */
    var displayRotation: Int = Surface.ROTATION_0
        private set

    val isLockedPortrait: Boolean get() = true

    fun init(activity: Activity) {
        val rot = activity.display?.rotation ?: Surface.ROTATION_0
        displayRotation = when (rot) {
            Surface.ROTATION_180 -> Surface.ROTATION_180
            else -> Surface.ROTATION_0
        }
    }
}
