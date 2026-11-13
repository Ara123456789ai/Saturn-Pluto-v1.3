package com.ascendant.sentiment

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ascendant.sentiment.engine.AstroEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Runs every 15 minutes in the background and checks the coming hour for Saturn and Pluto entry windows. */
class AlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            AlertNotifier.check(applicationContext, System.currentTimeMillis())
        } catch (_: Exception) {
        }
        return Result.success()
    }
}

object AlertNotifier {
    private const val PREFS = "ps_prefs"
    private const val CHANNEL = "entry_alerts"

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("entry-alerts", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun saveLocation(ctx: Context, lat: Double, lon: Double) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("lat", java.lang.Double.doubleToRawLongBits(lat))
            .putLong("lon", java.lang.Double.doubleToRawLongBits(lon))
            .apply()
    }

    fun isEnabled(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", on).apply()
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Entry windows", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(ctx: Context, id: Int, title: String, text: String) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    fun sendTest(ctx: Context) {
        post(ctx, 9001, "Pluto–Saturn test", "Notifications are working. Entry windows will appear here.")
    }

    fun describe(a: com.ascendant.sentiment.model.EntryAlert, nowMs: Long): String {
        val glyph = if (a.target == "Saturn") "♄" else "♇"
        val hm = SimpleDateFormat("HH:mm", Locale.US)
        val span = hm.format(Date(a.firstMs)) + "–" + hm.format(Date(a.lastMs + 300_000L))
        val mins = ((a.firstMs - nowMs) / 60_000L).toInt()
        val when_ = if (a.activeNow || mins <= 0) "active now" else "in ~$mins min"
        return "Asc ${a.ascAngle}° $glyph with ☽ ${a.moonAngle}° $glyph · $span (phone time) · $when_"
    }

    fun check(ctx: Context, nowMs: Long) {
        if (!isEnabled(ctx)) return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat = java.lang.Double.longBitsToDouble(prefs.getLong("lat", java.lang.Double.doubleToRawLongBits(51.5074)))
        val lon = java.lang.Double.longBitsToDouble(prefs.getLong("lon", java.lang.Double.doubleToRawLongBits(-0.1278)))
        val seen = LinkedHashSet(prefs.getStringSet("seen", emptySet()) ?: emptySet())
        var changed = false
        for ((target, glyph, id) in listOf(Triple("Saturn", "♄", 9101), Triple("Pluto", "♇", 9102))) {
            val a = AstroEngine.entryAlert(nowMs, lat, lon, target) ?: continue
            val key = "$target|${a.ascAngle}|${a.moonAngle}|${a.firstMs / 86_400_000L}"
            if (key in seen) continue
            seen.add(key)
            changed = true
            post(ctx, id, "$glyph $target entry window", describe(a, nowMs))
        }
        if (changed) {
            val keep = seen.toList().takeLast(30).toSet()
            prefs.edit().putStringSet("seen", keep).apply()
        }
    }
}
