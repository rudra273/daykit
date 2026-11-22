package com.daykit.feature.eventlight.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.daykit.R
import com.daykit.feature.eventlight.data.EventLightStore
import com.daykit.feature.widget.EventLightWidgetProvider

/**
 * Watches front-facing camera availability via [CameraManager.AvailabilityCallback]
 * (no CAMERA permission required) and posts a lightweight prompt notification when
 * another app opens the front camera while Edge Light is off, letting the user turn
 * Edge Light on with one tap from the notification shade.
 */
object FrontCameraMonitor {
    private const val TAG = "FrontCameraMonitor"
    private const val CHANNEL_ID = "event_light_front_camera"
    private const val NOTIFICATION_ID = 2002

    private var registered = false
    private val activeFrontCameras = mutableSetOf<String>()

    fun register(context: Context) {
        if (registered) return
        val appContext = context.applicationContext
        val cameraManager = appContext.getSystemService(CameraManager::class.java) ?: return
        val handler = Handler(Looper.getMainLooper())

        val callback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraUnavailable(cameraId: String) {
                if (!isFrontCamera(cameraManager, cameraId)) return
                val wasEmpty = activeFrontCameras.isEmpty()
                activeFrontCameras.add(cameraId)
                if (wasEmpty) {
                    onFrontCameraOpened(appContext)
                }
            }

            override fun onCameraAvailable(cameraId: String) {
                if (!activeFrontCameras.remove(cameraId)) return
                if (activeFrontCameras.isEmpty()) {
                    dismissNotification(appContext)
                }
            }
        }

        runCatching {
            cameraManager.registerAvailabilityCallback(callback, handler)
            registered = true
        }.onFailure { error ->
            Log.w(TAG, "Could not register front camera callback", error)
        }
    }

    fun dismissNotification(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }

    private fun isFrontCamera(cameraManager: CameraManager, cameraId: String): Boolean {
        return runCatching {
            val facing = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_FRONT
        }.getOrDefault(false)
    }

    private fun onFrontCameraOpened(context: Context) {
        val settings = EventLightStore.get(context)
        if (settings.enabled) return
        if (!Settings.canDrawOverlays(context)) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Front Camera Edge Light",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Suggests turning on Edge Light when the front camera is active"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)

        val turnOnIntent = Intent(context, EventLightWidgetProvider::class.java).apply {
            action = EventLightWidgetProvider.ACTION_TURN_ON
        }
        val turnOnPendingIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            turnOnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Front camera is on")
            .setContentText("Tap to turn on Edge Light")
            .setContentIntent(turnOnPendingIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Turn on",
                    turnOnPendingIntent,
                ).build(),
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        runCatching {
            manager.notify(NOTIFICATION_ID, notification)
        }.onFailure { error ->
            Log.w(TAG, "Could not post front camera notification", error)
        }
    }
}
