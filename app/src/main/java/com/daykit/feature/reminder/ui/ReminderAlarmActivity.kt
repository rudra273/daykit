package com.daykit.feature.reminder.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daykit.DayKitApplication
import com.daykit.core.designsystem.DayKitTheme
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.feature.reminder.notification.ReminderNotifier
import com.daykit.feature.reminder.notification.ReminderScheduler
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Full-screen reminder page shown when a reminder fires, modelled on the App Lock
 * [com.daykit.feature.applock.ui.LockActivity]. Launched via a full-screen intent so
 * it wakes and shows over the lock screen like an alarm, rather than sitting as a
 * silent notification.
 */
class ReminderAlarmActivity : ComponentActivity() {
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val reminderId: String
        get() = intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty()

    private val reminderTitle: String
        get() = intent.getStringExtra(EXTRA_TITLE).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLockedAndTurnScreenOn()
        enableEdgeToEdge()
        setContent {
            DayKitTheme {
                ReminderAlarmScreen(
                    title = reminderTitle,
                    onComplete = { complete() },
                    onDismiss = { snoozeToNotification() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    private fun complete() {
        val id = reminderId
        val appContext = applicationContext
        NotificationManagerCompat.from(appContext).cancel(ReminderNotifier.notificationId(id))
        ioScope.launch {
            val repository = (appContext as DayKitApplication).container.reminderRepository
            repository.markComplete(id)
            ReminderScheduler(appContext).cancel(id)
        }
        finish()
    }

    /**
     * Closing the full-screen page without completing falls back to the persistent
     * notification so the reminder is not lost.
     */
    private fun snoozeToNotification() {
        val id = reminderId
        val title = reminderTitle
        val appContext = applicationContext
        ioScope.launch {
            val reminder = (appContext as DayKitApplication).container.reminderRepository.getReminder(id)
            if (reminder != null && !reminder.completed) {
                ReminderNotifier.show(appContext, id, title)
            }
        }
        finish()
    }

    companion object {
        private const val EXTRA_REMINDER_ID = "reminder_id"
        private const val EXTRA_TITLE = "reminder_title"

        fun intent(context: Context, reminderId: String, title: String): Intent {
            return Intent(context, ReminderAlarmActivity::class.java)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_TITLE, title)
        }
    }
}

@Composable
private fun ReminderAlarmScreen(
    title: String,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Swallow back so the reminder is acknowledged deliberately, matching the lock screen.
    BackHandler(enabled = true) {}

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Reminder",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(48.dp))
            PrimaryButton(
                text = "Complete",
                modifier = Modifier.fillMaxWidth(),
                onClick = onComplete,
            )
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = "Dismiss",
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss,
            )
        }
    }
}
