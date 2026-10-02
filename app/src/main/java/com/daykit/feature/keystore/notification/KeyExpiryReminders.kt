package com.daykit.feature.keystore.notification

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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.daykit.MainActivity
import com.daykit.R
import com.daykit.feature.keystore.data.KeyExpiry
import com.daykit.feature.keystore.data.KeyStoreEntry
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Card and ID expiry notifications for the Key Store.
 *
 * The expiry date is encrypted under the MSK, so nothing in the background can read it.
 * Instead each reminder is scheduled as a one-off WorkManager job while the app is
 * unlocked: on every save, and in full by [sync] on each unlock. The job carries only the
 * entry id, a kind and a date (WorkManager stores its input in plain storage), so the
 * notification is deliberately generic: it never names the card, bank or document.
 * WorkManager persists jobs across reboots, so no boot receiver is needed.
 */
class KeyExpiryReminders(context: Context) {
    private val appContext = context.applicationContext
    private val workManager: WorkManager get() = WorkManager.getInstance(appContext)

    /** Schedules (or replaces, or cancels) the reminder for one saved entry. */
    fun onSaved(entry: KeyStoreEntry) {
        val reminder = KeyExpiry.reminderFor(entry)
        if (reminder == null) {
            workManager.cancelUniqueWork(workName(entry.entryId))
            return
        }
        val fireAtMillis = reminder.remindOn
            .atTime(REMIND_AT)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val request = OneTimeWorkRequestBuilder<KeyExpiryWorker>()
            .setInitialDelay((fireAtMillis - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    KEY_ENTRY_ID to entry.entryId,
                    KEY_KIND to reminder.kind.storageKey,
                    KEY_EXPIRES_ON to reminder.expiresOn.toString(),
                ),
            )
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(entry.entryId), ExistingWorkPolicy.REPLACE, request)
    }

    /** Needs no MSK, so it also works for trash purges before unlock. */
    fun onRemoved(entryId: String) {
        workManager.cancelUniqueWork(workName(entryId))
        NotificationManagerCompat.from(appContext).cancel(notificationId(entryId))
    }

    /**
     * Rebuilds every reminder from the live entries. Catches what per-save scheduling can't:
     * entries saved before reminders existed, and jobs lost to a cleared app cache.
     * WorkManager runs these operations in order, so cancel-then-enqueue is safe.
     */
    fun sync(entries: List<KeyStoreEntry>) {
        workManager.cancelAllWorkByTag(TAG)
        entries.forEach(::onSaved)
    }

    companion object {
        const val CHANNEL_ID = "key_store_expiry"
        internal const val TAG = "key_store_expiry"
        internal const val KEY_ENTRY_ID = "entry_id"
        internal const val KEY_KIND = "kind"
        internal const val KEY_EXPIRES_ON = "expires_on"

        /** Mid-morning, not the midnight the date alone would mean. */
        private val REMIND_AT: LocalTime = LocalTime.of(10, 0)

        private fun workName(entryId: String) = "$TAG:$entryId"

        internal fun notificationId(entryId: String): Int = "$TAG:$entryId".hashCode()
    }
}

class KeyExpiryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryId = inputData.getString(KeyExpiryReminders.KEY_ENTRY_ID) ?: return Result.success()
        val kind = KeyExpiry.Kind.fromStorageKey(inputData.getString(KeyExpiryReminders.KEY_KIND))
            ?: return Result.success()
        val expiresOn = runCatching { LocalDate.parse(inputData.getString(KeyExpiryReminders.KEY_EXPIRES_ON)) }
            .getOrNull() ?: return Result.success()
        show(entryId, kind, expiresOn)
        return Result.success()
    }

    private fun show(entryId: String, kind: KeyExpiry.Kind, expiresOn: LocalDate) {
        val context = applicationContext
        ensureChannel(context)
        // The runtime permission only exists from Android 13; below it the check would deny.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val (title, text) = when (kind) {
            KeyExpiry.Kind.Card -> {
                val month = expiresOn.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                "A saved card expires this month" to
                    "It's valid until the end of $month. Open Key Store to save your new card."
            }
            KeyExpiry.Kind.Document -> {
                val date = expiresOn.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                "A saved ID document expires soon" to
                    "It's valid until $date. Open Key Store for the details."
            }
        }
        val openApp = PendingIntent.getActivity(
            context,
            KeyExpiryReminders.notificationId(entryId),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, KeyExpiryReminders.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(KeyExpiryReminders.notificationId(entryId), notification)
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            KeyExpiryReminders.CHANNEL_ID,
            "Card & ID expiry",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A heads-up before a card or ID document saved in Key Store expires."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
