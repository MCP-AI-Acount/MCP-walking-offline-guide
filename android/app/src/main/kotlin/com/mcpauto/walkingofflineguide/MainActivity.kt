package com.mcpauto.walkingofflineguide

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mcpauto.walkingofflineguide.logic.LandscapeOrientation
import com.mcpauto.walkingofflineguide.ui.WalkingApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        super.onCreate(savedInstanceState)
        LandscapeOrientation.init(this)
        enableEdgeToEdge()
        hideNavigationBar()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                WalkingApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LandscapeOrientation.init(this)
        hideNavigationBar()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        LandscapeOrientation.init(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()
    }

    /** 앱 사용 중 하단 내비게이션(뒤로·홈·최근) 숨김 — 화면 가장자리 스와이프 시 잠깐 표시 */
    private fun hideNavigationBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
