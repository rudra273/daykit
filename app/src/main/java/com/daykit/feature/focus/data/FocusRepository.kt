package com.daykit.feature.focus.data

import com.daykit.core.session.AppLockSessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay

/**
 * Owns "focus blocks": self-imposed timed locks that cannot be opened even with
 * the correct PIN. Deliberately independent of App Lock's locked-package set — a
 * block can exist on an app the user never PIN-locked, and enforcement in
 * `AppMonitorService` checks it ahead of the PIN gate.
 */
class FocusRepository(
    private val focusBlockStore: FocusBlockStore,
) {
    /** Bumped on every write so observers re-read the store. */
    private val revision = MutableStateFlow(0)

    /**
     * Emits the currently-active focus blocks, re-emitting both on change and
     * when the next block expires.
     *
     * The expiry half matters: the store prunes lazily on read, so without a
     * scheduled emission a collector would keep showing a block whose timer has
     * already run out until something happened to write to the store. Callers
     * therefore don't need their own polling loop to notice expiry (a UI showing
     * a live countdown still needs a per-second tick, but only to re-render the
     * remaining-time text).
     */
    fun observeFocusBlocks(): Flow<List<FocusBlock>> = channelFlow {
        // collectLatest restarts this block on every write, so a newly-started
        // block immediately replaces a pending expiry wait.
        revision.collectLatest {
            while (true) {
                val now = System.currentTimeMillis()
                val active = focusBlockStore.getActiveBlocks(now)
                send(active)

                // No active blocks: nothing to schedule. Park until the next
                // write restarts this block, rather than waking on a timer for
                // as long as the collector lives.
                val nextExpiry = active.minOfOrNull { it.lockUntilMillis } ?: break
                // Capped because delay() counts elapsed real time while
                // lockUntilMillis is wall-clock: a clock change or a long doze
                // would otherwise leave us sleeping past (or short of) the real
                // expiry. Re-reading at least this often makes the wait
                // self-correcting, and the store prunes on read anyway.
                val wait = (nextExpiry - now).coerceIn(1L, MAX_EXPIRY_WAIT_MILLIS)
                delay(wait)
            }
        }
    }

    /**
     * Starts a strict timed lock on [packageName] lasting [durationMillis].
     * Until it expires the app cannot be opened, even with the correct PIN.
     */
    suspend fun startFocusBlock(packageName: String, label: String, durationMillis: Long) {
        val lockUntilMillis = System.currentTimeMillis() + durationMillis
        focusBlockStore.startBlock(packageName, label, lockUntilMillis)
        // Drop any live PIN-unlock grant so that when the block expires the app
        // falls back to its true state (PIN gate if locked) instead of being
        // opened by a grant that outlived the block. Without this, unlocking an
        // app and then starting a short block would leave it openable at expiry.
        AppLockSessionManager.revoke(packageName)
        revision.value += 1
    }

    /** Expiry timestamp for an active focus block on [packageName], else null. */
    fun focusBlockUntil(packageName: String): Long? = focusBlockStore.lockUntil(packageName)

    fun activeFocusPackages(): Set<String> = focusBlockStore.activePackages()

    /** Re-reads the store and drops any blocks whose timers have expired. */
    fun refreshFocusBlocks() {
        focusBlockStore.getActiveBlocks()
        revision.value += 1
    }

    private companion object {
        /** Longest a collector will sleep before re-checking expiry. */
        const val MAX_EXPIRY_WAIT_MILLIS = 60_000L
    }
}
