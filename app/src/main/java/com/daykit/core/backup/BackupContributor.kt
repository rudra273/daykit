package com.daykit.core.backup

import org.json.JSONObject

interface BackupContributor {
    val toolKey: String
    val schemaVersion: Int

    suspend fun exportJson(): JSONObject

    suspend fun importJson(payload: JSONObject)
}

/** Contributor [BackupContributor.toolKey] values, shared by every backup caller. */
object BackupToolKeys {
    const val KEY_STORE = "key_store"
    const val NOTES = "secure_notes"
    const val EXPENSES = "expenses"
    const val HABITS = "habits"
    const val VAULT = "vault_files"
}

/**
 * The single definition of what a backup contains, so the manual path and the
 * scheduled path cannot disagree.
 *
 * Every backup runs with the PIN-derived key held in memory, so Key Store and
 * Secure Notes are unconditional — they are the data a user is least able to
 * reconstruct. Expenses, Habits, and Vault files are opt-in and all default off;
 * Vault has its own toggle because its file blobs can make a backup very large.
 */
fun includedBackupToolKeys(
    includeExpenses: Boolean,
    includeHabits: Boolean,
    includeVault: Boolean,
): Set<String> = buildSet {
    add(BackupToolKeys.KEY_STORE)
    add(BackupToolKeys.NOTES)
    if (includeVault) add(BackupToolKeys.VAULT)
    if (includeExpenses) add(BackupToolKeys.EXPENSES)
    if (includeHabits) add(BackupToolKeys.HABITS)
}
