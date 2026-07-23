package com.daykit.feature.eventlight.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.daykit.MainActivity
import com.daykit.R
import com.daykit.feature.eventlight.data.EventLightStore
import com.daykit.feature.eventlight.data.PREF_EVENT_LIGHT

/**
 * Keeps [EventLightOverlayController] alive while the user is in another app (e.g. a
 * video call app). No polling loop is needed — the overlay is shown once in [onCreate]
 * and live-updated whenever [EventLightStore] changes, following the same
 * startForeground/companion start-stop shape as
 * [com.daykit.feature.applock.service.AppMonitorService].
 */
class EventLightService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var overlayController: EventLightOverlayController
    private var prefs: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        overlayController = EventLightOverlayController(this)
        overlayController.show(EventLightStore.get(this))

        val p = getSharedPreferences(PREF_EVENT_LIGHT, Context.MODE_PRIVATE)
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            mainHandler.post { overlayController.update(EventLightStore.get(this)) }
        }
        p.registerOnSharedPreferenceChangeListener(l)
        prefs = p
        listener = l
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        prefs?.let { p -> listener?.let { p.unregisterOnSharedPreferenceChangeListener(it) } }
        prefs = null
        listener = null
        mainHandler.post { overlayController.dismiss() }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Event Light",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Keeps the Event Light border showing"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Event Light is on")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "event_light"
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, EventLightService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EventLightService::class.java))
        }
    }
}
