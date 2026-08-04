package com.daykit.feature.focus.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists "focus blocks": a self-imposed timed lock on an app. While a block is
 * active the app cannot be opened even with the correct PIN — the timer must
 * expire first. Stored in SharedPreferences (not the in-memory session manager)
 * so a block survives process death, screen-off session resets, and reboot.
 *
 * Deliberately separate from App Lock's `LockedPackageCache`: a focus block is
 * independent of whether an app is PIN-locked, so a timer can exist on an app
 * the user has not added to the regular locked set.
 */
class FocusBlockStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the currently-active blocks, dropping any that have already
     * expired. Expired entries are also pruned from storage on read so the file
     * does not accumulate stale records.
     */
    fun getActiveBlocks(nowMillis: Long = System.currentTimeMillis()): List<FocusBlock> {
        val all = readAll()
        val active = all.filter { it.lockUntilMillis > nowMillis }
        if (active.size != all.size) {
            writeAll(active)
        }
        return active
    }

    fun activePackages(nowMillis: Long = System.currentTimeMillis()): Set<String> {
        return getActiveBlocks(nowMillis).map { it.packageName }.toSet()
    }

    /** Expiry timestamp for one package if it still has an active block, else null. */
    fun lockUntil(packageName: String, nowMillis: Long = System.currentTimeMillis()): Long? {
        return getActiveBlocks(nowMillis).firstOrNull { it.packageName == packageName }?.lockUntilMillis
    }

    /** Starts (or replaces) a focus block for a package, ending at [lockUntilMillis]. */
    fun startBlock(packageName: String, label: String, lockUntilMillis: Long) {
        val now = System.currentTimeMillis()
        val updated = readAll()
            .filterNot { it.packageName == packageName }
            .filter { it.lockUntilMillis > now }
            .toMutableList()
        updated.add(FocusBlock(packageName, label, lockUntilMillis))
        writeAll(updated)
    }

    /**
     * Merges [blocks] into the store, keeping the later expiry when a package is
     * present in both. Used by backup import; expired entries are dropped rather
     * than resurrected, so restoring an old backup can't re-block an app whose
     * timer already ran out.
     */
    fun mergeBlocks(blocks: List<FocusBlock>, nowMillis: Long = System.currentTimeMillis()) {
        val incoming = blocks.filter { it.lockUntilMillis > nowMillis }
        if (incoming.isEmpty()) return
        val byPackage = readAll()
            .filter { it.lockUntilMillis > nowMillis }
            .associateBy { it.packageName }
            .toMutableMap()
        incoming.forEach { block ->
            val existing = byPackage[block.packageName]
            if (existing == null || block.lockUntilMillis > existing.lockUntilMillis) {
                byPackage[block.packageName] = block
            }
        }
        writeAll(byPackage.values.toList())
    }

    private fun readAll(): List<FocusBlock> {
        val raw = prefs.getString(KEY_BLOCKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                FocusBlock(
                    packageName = item.getString(KEY_PACKAGE_NAME),
                    label = item.optString(KEY_LABEL, item.getString(KEY_PACKAGE_NAME)),
                    lockUntilMillis = item.optLong(KEY_LOCK_UNTIL, 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(blocks: List<FocusBlock>) {
        val array = JSONArray()
        blocks
            .distinctBy { it.packageName }
            .forEach { block ->
                array.put(
                    JSONObject()
                        .put(KEY_PACKAGE_NAME, block.packageName)
                        .put(KEY_LABEL, block.label)
                        .put(KEY_LOCK_UNTIL, block.lockUntilMillis),
                )
            }
        prefs.edit(commit = true) {
            putString(KEY_BLOCKS, array.toString())
        }
    }

    /**
     * Drops every focus block. Committed synchronously for the same reason as
     * App Lock's `LockedPackageCache.clear` — the monitor service reads this
     * directly.
     */
    fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val PREFS_NAME = "app_lock_focus_blocks"
        const val KEY_BLOCKS = "blocks"
        const val KEY_PACKAGE_NAME = "packageName"
        const val KEY_LABEL = "label"
        const val KEY_LOCK_UNTIL = "lockUntilMillis"
    }
}

data class FocusBlock(
    val packageName: String,
    val label: String,
    val lockUntilMillis: Long,
)
