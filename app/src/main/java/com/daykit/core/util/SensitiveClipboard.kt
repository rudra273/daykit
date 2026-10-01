package com.daykit.core.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daykit.core.data.AppPreferences
import java.util.concurrent.TimeUnit

/**
 * Copies a secret so it is flagged sensitive (hidden from the Android 13+
 * clipboard preview and keyboard suggestions) and, unless the user chose
 * "Never", cleared after [AppPreferences.clipboardClearSeconds].
 *
 * The clear runs through WorkManager rather than a Handler: DayKit is usually
 * in the background by then and its process may be frozen. A newer copy
 * replaces the pending clear, so the timer always counts from the last copy.
 */
object SensitiveClipboard {
    private const val WORK_NAME = "sensitive-clipboard-clear"

    fun copy(context: Context, label: String, value: String) {
        val appContext = context.applicationContext
        val clip = ClipData.newPlainText(label, value).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        appContext.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)

        val workManager = WorkManager.getInstance(appContext)
        val seconds = AppPreferences.clipboardClearSeconds
        if (seconds <= 0) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ClearWorker>()
                .setInitialDelay(seconds.toLong(), TimeUnit.SECONDS)
                .build(),
        )
    }

    class ClearWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            // A background app cannot read the clipboard to check it is still ours,
            // so clear unconditionally; copying elsewhere afterwards is rare enough.
            runCatching { applicationContext.getSystemService(ClipboardManager::class.java)?.clearPrimaryClip() }
            return Result.success()
        }
    }
}
