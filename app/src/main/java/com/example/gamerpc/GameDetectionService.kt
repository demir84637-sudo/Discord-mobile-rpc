package com.example.gamerpc

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Polls the system for the current foreground app every few seconds.
 * If that app is categorized as a game (or the user has enabled it manually),
 * shows a persistent notification styled like:
 *
 *   PLAYING ON ANDROID
 *   <Game Name>
 *   <mm:ss elapsed>
 *
 * This never touches any Discord account, token, or API — it is purely local
 * detection + a local notification.
 */
class GameDetectionService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentGamePackage: String? = null
    private var sessionStartMillis: Long = 0L

    private val pollInterval = 4000L // 4 seconds

    companion object {
        const val CHANNEL_ID = "game_rpc_channel"
        const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildIdleNotification())
        handler.post(pollRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            val foregroundPackage = getForegroundPackage()
            handleForegroundPackage(foregroundPackage)
            handler.postDelayed(this, pollInterval)
        }
    }

    private fun handleForegroundPackage(pkg: String?) {
        if (pkg == null || !isLikelyGame(pkg)) {
            if (currentGamePackage != null) {
                // Left the game
                currentGamePackage = null
                updateNotification(buildIdleNotification())
            }
            return
        }

        if (pkg != currentGamePackage) {
            // New game session started
            currentGamePackage = pkg
            sessionStartMillis = System.currentTimeMillis()
        }

        val elapsed = System.currentTimeMillis() - sessionStartMillis
        val gameName = getAppLabel(pkg)
        updateNotification(buildPlayingNotification(gameName, elapsed))
    }

    /** Uses UsageStatsManager to find which app is currently in the foreground. */
    private fun getForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 10_000
        val events = usm.queryEvents(begin, end)
        var lastPkg: String? = null
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPkg = event.packageName
            }
        }
        return lastPkg
    }

    /** Checks Play Store "game" category, falling back to a simple heuristic. */
    private fun isLikelyGame(pkg: String): Boolean {
        if (pkg == packageName) return false
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                info.category == ApplicationInfo.CATEGORY_GAME
            } else {
                @Suppress("DEPRECATION")
                (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            }
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Game RPC Presence",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Şu an oynadığın oyunu gösterir"
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildIdleNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Game RPC aktif")
            .setContentText("Bir oyun açmanı bekliyor…")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .build()
    }

    private fun buildPlayingNotification(gameName: String, elapsedMillis: Long): Notification {
        val totalSeconds = elapsedMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val timer = String.format("%02d:%02d", minutes, seconds)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PLAYING ON ANDROID")
            .setContentText("$gameName  •  $timer")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(notification: Notification) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, notification)
    }
}
