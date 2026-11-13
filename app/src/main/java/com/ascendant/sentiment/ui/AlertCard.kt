package com.ascendant.sentiment.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascendant.sentiment.model.EntryAlert
import com.ascendant.sentiment.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Entry windows for the hour after the cursor: Ascendant hard aspect to Saturn (or Pluto) while the Moon aspects that planet.
 * The same check runs in the background every 15 minutes and sends a phone notification.
 */
@Composable
fun AlertCard(
    saturn: EntryAlert?,
    pluto: EntryAlert?,
    fromMs: Long,
    timeZoneId: String,
    notifyOn: Boolean,
    onNotifyChange: (Boolean) -> Unit,
    onTest: () -> Unit
) {
    val hm = remember(timeZoneId) { SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(timeZoneId) } }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ENTRY WINDOWS · NEXT HOUR FROM ${hm.format(Date(fromMs))}", color = Slate100, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                "Asc hard aspect (45/90/135/150/180°) to the planet while the Moon aspects it (♄: 60/90/120/135/150°, ♇: 90/135°). Orb 3°.",
                color = Slate400, fontSize = 9.sp, lineHeight = 12.sp
            )
            listOf("Saturn" to saturn, "Pluto" to pluto).forEach { (name, a) ->
                val glyph = if (name == "Saturn") "♄" else "♇"
                if (a == null) {
                    Text("$glyph $name · no window in the next hour", color = Slate400, fontSize = 12.sp)
                } else {
                    val mins = ((a.firstMs - fromMs) / 60_000L).toInt()
                    Text(
                        "$glyph $name · Asc ${a.ascAngle}° with ☽ ${a.moonAngle}° · ${hm.format(Date(a.firstMs))}–${hm.format(Date(a.lastMs + 300_000L))} · " +
                            if (a.activeNow || mins <= 0) "ACTIVE NOW" else "in ~$mins min",
                        color = Amber500, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = notifyOn, onCheckedChange = onNotifyChange, colors = SwitchDefaults.colors(checkedTrackColor = Emerald500))
                Spacer(Modifier.width(6.dp))
                Text("Phone notifications", fontSize = 11.sp, color = Slate100, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onTest, shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(30.dp)
                ) { Text("Test", fontSize = 11.sp, color = Slate100) }
            }
        }
    }
}
