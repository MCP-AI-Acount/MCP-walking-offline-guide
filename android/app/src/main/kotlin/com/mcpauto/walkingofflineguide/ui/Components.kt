package com.mcpauto.walkingofflineguide.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import com.mcpauto.walkingofflineguide.data.Poi
import com.mcpauto.walkingofflineguide.logic.PoiLogic

private fun formatDistance(m: Int): String =
    if (m >= 1000) "%.1f km".format(m / 1000.0) else "$m m"

@Composable
fun StarRating(rating: Float, modifier: Modifier = Modifier) {
    val clamped = rating.coerceIn(0f, 5f)
    val full = clamped.toInt().coerceIn(0, 5)
    val stars = buildString {
        repeat(full) { append('★') }
        repeat(5 - full) { append('☆') }
    }
    Text(
        "$stars ${"%.1f".format(clamped)}",
        color = Color(0xFFF59E0B),
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
    )
}

@Composable
fun PoiListItem(
    poi: Poi,
    displayName: String,
    description: String?,
    accent: Color,
    typeLabel: String,
    routePrefix: String,
    speakLabel: String,
    selected: Boolean = false,
    routeDistanceM: Int? = null,
    onClick: () -> Unit = {},
    onSpeak: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick)
            .then(
                if (selected) {
                    Modifier.border(BorderStroke(2.dp, Color(0xFFF59E0B)), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(typeLabel, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                StarRating(PoiLogic.displayRating(poi), modifier = Modifier.padding(top = 2.dp))
                poi.distanceM?.let { Text(formatDistance(it), color = accent, style = MaterialTheme.typography.labelSmall) }
                routeDistanceM?.let {
                    Text("$routePrefix ${formatDistance(it)}", color = Color(0xFF2563EB), style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onSpeak) {
                Icon(Icons.Default.VolumeUp, contentDescription = speakLabel, tint = accent)
            }
        }
    }
}
