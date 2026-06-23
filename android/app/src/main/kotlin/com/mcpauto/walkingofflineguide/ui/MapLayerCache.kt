package com.mcpauto.walkingofflineguide.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect
import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.TileStore
import com.mcpauto.walkingofflineguide.logic.MapCamera
import com.mcpauto.walkingofflineguide.logic.MapCameraMath
import com.mcpauto.walkingofflineguide.network.OnlineTileProvider

internal object MapTileDraw {
    private val overzoomCache = android.util.LruCache<String, Bitmap>(24)
    private val nativeBmpCache = android.util.LruCache<String, Bitmap>(32)
    private const val MAX_TILES_DRAWN = 480

    fun drawTiles(
        canvas: AndroidCanvas,
        tileStore: TileStore?,
        secondary: TileStore?,
        online: OnlineTileProvider?,
        renderCam: MapCamera,
        z: Int,
        xSpan: IntRange,
        ySpan: IntRange,
        scale: Float,
        cx: Double,
        cy: Double,
        originX: Float,
        originY: Float,
        tilePx: Int,
        paint: Paint,
        screenCompX: Float = 0f,
        screenCompY: Float = 0f,
        clipBbox: Bbox? = null,
    ) {
        var drawn = 0
        for (x in xSpan) {
            for (y in ySpan) {
                if (drawn >= MAX_TILES_DRAWN) return
                if (clipBbox != null && !MapCameraMath.tileIntersectsBbox(x, y, z, clipBbox)) continue
                val bmp = resolveTileBitmap(tileStore, secondary, online, z, x, y) ?: continue
                val left = ((x * MapCameraMath.TILE_LOGICAL_PX - cx) * scale + originX + screenCompX).toInt()
                val top = ((y * MapCameraMath.TILE_LOGICAL_PX - cy) * scale + originY + screenCompY).toInt()
                val dst = Rect(left, top, left + tilePx, top + tilePx)
                canvas.drawBitmap(nativeBitmap(bmp, "$z/$x/$y"), null, dst, paint)
                drawn++
            }
        }
    }

    private fun nativeBitmap(source: Bitmap, key: String): Bitmap {
        if (source.config != Bitmap.Config.HARDWARE || source.isRecycled) return source
        nativeBmpCache.get(key)?.let { return it }
        val copy = source.copy(Bitmap.Config.RGB_565, false) ?: source
        nativeBmpCache.put(key, copy)
        return copy
    }

    private fun resolveTileBitmap(
        tileStore: TileStore?,
        secondary: TileStore?,
        online: OnlineTileProvider?,
        z: Int,
        x: Int,
        y: Int,
    ): Bitmap? {
        tileStore?.get(z, x, y)?.let { return it }
        secondary?.get(z, x, y)?.let { return it }
        online?.get(z, x, y)?.let { return it }
        if (z <= 10) return null
        val cacheKey = "$z/$x/$y"
        overzoomCache.get(cacheKey)?.let { return it }
        val parent = resolveTileBitmap(tileStore, secondary, online, z - 1, x / 2, y / 2) ?: return null
        val halfW = parent.width / 2
        val halfH = parent.height / 2
        val sx = if (x % 2 == 1) halfW else 0
        val sy = if (y % 2 == 1) halfH else 0
        return Bitmap.createBitmap(
            parent, sx, sy,
            halfW.coerceAtMost(parent.width - sx),
            halfH.coerceAtMost(parent.height - sy),
        ).also { overzoomCache.put(cacheKey, it) }
    }
}

internal fun metersToScreenRadius(visibleSpanM: Double, screenW: Float, radiusM: Double): Float {
    val mpp = visibleSpanM / screenW.coerceAtLeast(1f)
    return (radiusM / mpp).toFloat().coerceAtLeast(8f)
}
