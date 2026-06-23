package com.mcpauto.walkingofflineguide.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object MapIntents {
    fun openGoogleMaps(context: Context, lat: Double, lon: Double, label: String = "") {
        val q = if (label.isNotBlank()) {
            "$lat,$lon(${Uri.encode(label)})"
        } else {
            "$lat,$lon"
        }
        val mapsIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:$lat,$lon?q=$q"),
        ).apply { setPackage("com.google.android.apps.maps") }
        runCatching { context.startActivity(mapsIntent) }.onFailure {
            val web = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon"),
            )
            context.startActivity(web)
        }
    }
}
