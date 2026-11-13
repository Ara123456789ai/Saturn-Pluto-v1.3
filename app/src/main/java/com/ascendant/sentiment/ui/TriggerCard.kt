package com.ascendant.sentiment.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascendant.sentiment.model.TriggerCardData
import com.ascendant.sentiment.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun shortLabel(glyph: String, angles: List<Int>): String =
    if (angles.size == 1) glyph + angles[0] else glyph

/**
 * Pluto–Saturn app: the trigger card, 1-hour bars.
 * Rows from the top: the Moon–Saturn and Moon–Pluto lanes, the Ascendant lanes (Asc–Saturn 120/135/150 and Asc–Pluto 135),
 * and the hard aspects (90, 135, 150, 180) to the Moon from the neutrals, Saturn and Pluto.
 */
@Composable
fun TriggerCard(
    data: TriggerCardData,
    trend: DoubleArray?,
    days: Int,
    inspectedTimeMs: Long,
    nowMs: Long,
    timeZoneId: String,
    onDaysChange: (Int) -> Unit,
    onDayStep: (Int) -> Unit,
    onInspectTime: (Long) -> Unit
) {
    val n = data.n
    if (n == 0) return
    val start = data.startMs
    val barMs = data.barMs
    val end = start + n * barMs
    val dayFmt = remember(timeZoneId) { SimpleDateFormat("EEE d", Locale.US).apply { timeZone = TimeZone.getTimeZone(timeZoneId) } }
    val tFmt = remember(timeZoneId) { SimpleDateFormat("EEE HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(timeZoneId) } }
    val hmFmt = remember(timeZoneId) { SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone(timeZoneId) } }
    val onInspect = rememberUpdatedState(onInspectTime)
    val selIdx = ((inspectedTimeMs - start) / barMs).toInt().coerceIn(0, n - 1)
    val lanes = data.lanes
    val groupSizes = intArrayOf(7, 2, 6)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderDark))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("MOON LANES · ASCENDANT LANES · HARD ASPECTS TO THE MOON (1h bars)", color = Slate100, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                "Top: ☽–♄ (60/90/120/135/150°) and ☽–♇ (90/135°) · middle: Asc–♄ (120/135/150°, coloured by angle) and Asc–♇ 135° · " +
                    "bottom: ☿ ♂ ☉ ♅ ♄ ♇ hard aspects (90/135/150/180°) to the Moon · brighter = closer to exact · " +
                    "background tint: heliocentric Sun field, green rising, red falling",
                color = Slate400, fontSize = 9.sp, lineHeight = 12.sp
            )
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onDayStep(-1) }, shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp), modifier = Modifier.size(width = 36.dp, height = 28.dp)
                ) { Text("◀", fontSize = 12.sp, color = Slate100) }
                OutlinedButton(
                    onClick = { onDayStep(1) }, shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp), modifier = Modifier.size(width = 36.dp, height = 28.dp)
                ) { Text("▶", fontSize = 12.sp, color = Slate100) }
                OutlinedButton(
                    onClick = { onDaysChange(if (days == 3) 7 else 3) }, shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(28.dp)
                ) { Text("${days}d", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate100) }
            }
            Spacer(Modifier.height(6.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .weekScrub(data, n, start, barMs, onInspect)
            ) {
                val left = 42.dp.toPx()
                val top = 6.dp.toPx()
                val bottom = 14.dp.toPx()
                val gap = 6.dp.toPx()
                val plotW = size.width - left - 12.dp.toPx()
                val rows = groupSizes.sum()
                val rowH = (size.height - top - bottom - gap * (groupSizes.size - 1)) / rows
                val cell = plotW / n
                val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 8.sp.toPx(); color = Slate400.toArgb(); textAlign = android.graphics.Paint.Align.RIGHT }

                fun rowY(r: Int): Float {
                    var g = 0
                    var acc = 0
                    while (g < groupSizes.size && r >= acc + groupSizes[g]) { acc += groupSizes[g]; g++ }
                    return top + r * rowH + g * gap
                }
                val plotBottom = rowY(rows - 1) + rowH

                for (r in 0 until rows) {
                    val y = rowY(r)
                    drawRect(Color(0xFF0F172A), Offset(left, y), Size(plotW, rowH - 1.dp.toPx()))
                    drawContext.canvas.nativeCanvas.drawText(shortLabel(lanes[r].glyph, lanes[r].angles), left - 4.dp.toPx(), y + rowH * 0.7f, paint)
                }

                // heliocentric Sun field tint behind the lanes: green while the heliocentric total rises, red while it falls
                if (trend != null && trend.size == n) {
                    for (i in 0 until n) {
                        val d = trend[i]
                        if (d == 0.0) continue
                        drawRect(
                            (if (d > 0) Emerald500 else Rose500).copy(alpha = 0.22f),
                            Offset(left + i * cell, top), Size(cell + 0.5f, plotBottom - top)
                        )
                    }
                }

                for (r in 0 until rows) {
                    val y = rowY(r)
                    for (i in 0 until n) {
                        val a = data.angle[r][i]
                        if (a < 0) continue
                        val base = HelioAspectColors[a] ?: Color.White
                        val alpha = (0.4f + 0.6f * (1f - (data.orb[r][i] / 3.0).toFloat())).coerceIn(0.4f, 1f)
                        drawRect(base.copy(alpha = alpha), Offset(left + i * cell, y), Size(cell + 0.5f, rowH - 1.dp.toPx()))
                    }
                }

                var lastDay = ""
                for (i in 0 until n) {
                    val label = dayFmt.format(Date(start + i * barMs))
                    if (label != lastDay) {
                        lastDay = label
                        drawLine(Color(0xFF334155).copy(alpha = 0.8f), Offset(left + i * cell, top), Offset(left + i * cell, plotBottom), 1f)
                        paint.textAlign = android.graphics.Paint.Align.LEFT
                        drawContext.canvas.nativeCanvas.drawText(label, left + i * cell + 3.dp.toPx(), size.height - 2.dp.toPx(), paint)
                        paint.textAlign = android.graphics.Paint.Align.RIGHT
                    }
                }
                if (nowMs in start until end) {
                    val nx = left + ((nowMs - start).toFloat() / (end - start)) * plotW
                    drawLine(Color(0xFFFBBF24).copy(alpha = 0.7f), Offset(nx, top), Offset(nx, plotBottom), 1.5f)
                }
                val sx = left + (selIdx + 0.5f) * cell
                drawLine(Color.White, Offset(sx, top), Offset(sx, plotBottom), 2f)
            }

            Spacer(Modifier.height(6.dp))
            Text("At the cursor · ${tFmt.format(Date(inspectedTimeMs))}", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            var any = false
            for (l in lanes.indices) {
                val a = data.angle[l][selIdx]
                if (a < 0) continue
                any = true
                val t = data.atMs[l][selIdx]
                val lane = lanes[l]
                val text = when (lane.group) {
                    0 -> "☽ $a° ♄"
                    1 -> "☽ $a° ♇"
                    2 -> if (lane.angles.size == 1) "Asc $a° ♇" else "Asc $a° ♄"
                    else -> lane.glyph.dropLast(1) + " $a° ☽"
                }
                Text(
                    "$text · orb %.1f° · tightest ${hmFmt.format(Date(t))}".format(data.orb[l][selIdx]),
                    color = HelioAspectColors[a] ?: Slate100, fontSize = 11.sp
                )
            }
            if (!any) Text("No lane is lit in this hour.", color = Slate100, fontSize = 11.sp)
        }
    }
}
