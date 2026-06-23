package com.mcpauto.walkingofflineguide.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val mapPickerTileHttp = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

private const val MAP_PICKER_PAGE_LOADED = "map_picker_page_loaded"
private const val MAP_PICKER_REFRESH_TAG = 0x7F070001

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeafletMapPickerView(
    centerLat: Double,
    centerLon: Double,
    initialPickLat: Double?,
    initialPickLon: Double?,
    referenceLabel: String,
    referenceIsAirport: Boolean = true,
    routeMarkersJson: String = "[]",
    onCoordinatesPicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val initJs = remember(
        centerLat, centerLon, initialPickLat, initialPickLon, referenceLabel, referenceIsAirport, routeMarkersJson,
    ) {
        buildInitJs(
            centerLat, centerLon, initialPickLat, initialPickLon, referenceLabel, referenceIsAirport, routeMarkersJson,
        )
    }
    val refreshJs = remember(
        centerLat, centerLon, initialPickLat, initialPickLon, referenceLabel, referenceIsAirport, routeMarkersJson,
    ) {
        buildRefreshJs(
            centerLat, centerLon, initialPickLat, initialPickLon, referenceLabel, referenceIsAirport, routeMarkersJson,
        )
    }
    val webView = remember(context) {
        WebView(context).apply {
            tag = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(AndroidColor.parseColor("#CBD5E1"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT
                builtInZoomControls = false
                displayZoomControls = false
                userAgentString = settings.userAgentString + " WalkingOfflineGuide/1.2"
            }
            addJavascriptInterface(
                LeafletBridge(onCoordinatesPicked),
                "AndroidBridge",
            )
            val mainHandler = Handler(Looper.getMainLooper())
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    if (!request.isForMainFrame && url.startsWith("https://") &&
                        (url.contains("cartocdn.com") || url.contains("openstreetmap.org"))
                    ) {
                        return runCatching {
                            val resp = mapPickerTileHttp.newCall(
                                Request.Builder()
                                    .url(url)
                                    .header("User-Agent", "WalkingOfflineGuide/1.2 (map-picker)")
                                    .header("Accept", "image/png,*/*")
                                    .build(),
                            ).execute()
                            if (!resp.isSuccessful) return null
                            val body = resp.body ?: return null
                            WebResourceResponse(
                                resp.header("Content-Type", "image/png")?.substringBefore(";"),
                                null,
                                body.byteStream(),
                            )
                        }.getOrNull()
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    view ?: return
                    if (view.tag == MAP_PICKER_PAGE_LOADED) return
                    view.tag = MAP_PICKER_PAGE_LOADED
                    mainHandler.postDelayed({
                        view.evaluateJavascript(initJs, null)
                        view.evaluateJavascript("scheduleResize();", null)
                    }, 50)
                }
            }
            loadUrl("file:///android_asset/map/leaflet_picker.html")
        }
    }

    DisposableEffect(webView) {
        onDispose {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView
        },
        update = { view ->
            if (view.tag != MAP_PICKER_PAGE_LOADED) return@AndroidView
            val prev = view.getTag(MAP_PICKER_REFRESH_TAG) as? String
            if (prev != refreshJs) {
                view.setTag(MAP_PICKER_REFRESH_TAG, refreshJs)
                view.evaluateJavascript(refreshJs, null)
            }
        },
    )
}

private class LeafletBridge(
    private val onPick: (Double, Double) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onMapPick(lat: String, lon: String) {
        val la = lat.toDoubleOrNull() ?: return
        val lo = lon.toDoubleOrNull() ?: return
        main.post { onPick(la, lo) }
    }

    @JavascriptInterface
    fun onMapReady() {
        // no-op — hook for future loading UI
    }
}

private fun buildInitJs(
    centerLat: Double,
    centerLon: Double,
    initialPickLat: Double?,
    initialPickLon: Double?,
    referenceLabel: String,
    referenceIsAirport: Boolean,
    routeMarkersJson: String,
): String = buildMapJs("initMap", centerLat, centerLon, initialPickLat, initialPickLon, referenceLabel, referenceIsAirport, routeMarkersJson)

private fun buildRefreshJs(
    centerLat: Double,
    centerLon: Double,
    initialPickLat: Double?,
    initialPickLon: Double?,
    referenceLabel: String,
    referenceIsAirport: Boolean,
    routeMarkersJson: String,
): String = buildMapJs("refreshMap", centerLat, centerLon, initialPickLat, initialPickLon, referenceLabel, referenceIsAirport, routeMarkersJson)

private fun buildMapJs(
    fn: String,
    centerLat: Double,
    centerLon: Double,
    initialPickLat: Double?,
    initialPickLon: Double?,
    referenceLabel: String,
    referenceIsAirport: Boolean,
    routeMarkersJson: String,
): String {
    val plat = initialPickLat?.let { "%.6f".format(it) } ?: "null"
    val plon = initialPickLon?.let { "%.6f".format(it) } ?: "null"
    val label = referenceLabel.replace("\\", "\\\\").replace("'", "\\'")
    val refAirport = if (referenceIsAirport) "true" else "false"
    val markers = routeMarkersJson.replace("\\", "\\\\").replace("'", "\\'")
    return "$fn($centerLat,$centerLon,$plat,$plon,'$label',$refAirport,'$markers');"
}
