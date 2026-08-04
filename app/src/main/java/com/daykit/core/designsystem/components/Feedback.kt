package com.daykit.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import android.util.Log
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.extendedColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Snackbar-backed error channel for a screen.
 *
 * Screens hold state in plain `remember` with no ViewModel, so every write is a bare
 * `scope.launch { repository.x() }`. Without a catch, a failed write — most often a
 * `SensitiveDataLockedException` when the vault re-locks mid-edit — cancels the coroutine
 * silently and the UI reads as if the save succeeded.
 *
 * Create one with [rememberErrorReporter], wire [ErrorReporter.host] into the `Scaffold`'s
 * `snackbarHost`, and replace `scope.launch { … }` with [ErrorReporter.launchGuarded].
 */
@Stable
class ErrorReporter internal constructor(
    val host: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    /** Shows [message] on the snackbar. Safe to call from any thread via the screen scope. */
    fun show(message: String) {
        scope.launch { host.showSnackbar(message) }
    }

    /**
     * Runs [block] on the screen scope, reporting any failure as a snackbar instead of
     * letting it cancel silently. [onFailure] runs after the message is queued — use it to
     * roll back optimistic UI (closing a sheet, clearing a pending flag).
     *
     * [CancellationException] is rethrown so normal scope teardown is not swallowed.
     */
    fun launchGuarded(
        failureMessage: String,
        onFailure: () -> Unit = {},
        block: suspend () -> Unit,
    ): Job = scope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("DayKit", failureMessage, e)
            onFailure()
            host.showSnackbar(failureMessage)
        }
    }
}

@Composable
fun rememberErrorReporter(
    scope: CoroutineScope = rememberCoroutineScope(),
): ErrorReporter {
    val host = remember { SnackbarHostState() }
    return remember(host, scope) { ErrorReporter(host, scope) }
}

/**
 * Variant for screens that already own a [SnackbarHostState] and use it for success
 * messages too — reuses that host instead of adding a second, competing one.
 */
@Composable
fun rememberErrorReporter(
    host: SnackbarHostState,
    scope: CoroutineScope = rememberCoroutineScope(),
): ErrorReporter = remember(host, scope) { ErrorReporter(host, scope) }

/** Circular progress with an appearance delay (ported from GlassLoadingIndicator). */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    delayMillis: Long = 260L,
) {
    var visible by remember { mutableStateOf(delayMillis <= 0L) }
    LaunchedEffect(delayMillis) {
        if (delayMillis > 0L) {
            delay(delayMillis)
            visible = true
        }
    }
    if (!visible) return
    CircularProgressIndicator(
        modifier = modifier.size(36.dp),
        strokeWidth = 3.dp,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.textMuted,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(text = actionText, onClick = onAction)
        }
    }
}
