package com.mcpauto.walkingofflineguide.download

import android.content.Context
import com.mcpauto.walkingofflineguide.data.TripConfig
import com.mcpauto.walkingofflineguide.logic.MapPolicy
import com.mcpauto.walkingofflineguide.network.WifiGate

/** 모국 기본 설정·지도 진입 시 도보경로 그래프 선확보 (타일 대비 수백 KB 수준) */
object HomeRoutingBootstrap {
    suspend fun prefetchIfOnline(context: Context, config: TripConfig) {
        if (!WifiGate.hasInternet(context)) return
        val lat = config.homeLat.takeIf { it != 0.0 } ?: return
        val lon = config.homeLon.takeIf { it != 0.0 } ?: return
        OnDemandRouting.ensureGraph(
            context,
            MapPolicy.HOME_LIVE_ONLINE_ID,
            lat,
            lon,
            HomeProgressiveDownloader.HOME_RADIUS_KM,
        )
    }
}
