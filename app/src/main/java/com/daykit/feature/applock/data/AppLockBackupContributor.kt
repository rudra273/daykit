package com.daykit.feature.applock.data

import com.daykit.core.backup.BackupContributor
import com.daykit.core.backup.BackupToolKeys
import org.json.JSONArray
import org.json.JSONObject

/** Transfers selections only; the destination device keeps its own PIN and permissions. */
class AppLockBackupContributor(
    private val repository: AppLockRepository,
    private val ownPackage: String,
    private val onImported: () -> Unit,
) : BackupContributor {
    override val toolKey = BackupToolKeys.APP_LOCK
    override val schemaVersion = 1
    override suspend fun exportJson() = JSONObject().put("apps", JSONArray().also { rows ->
        repository.getLockedApps().forEach { rows.put(JSONObject().put("packageName", it.packageName).put("label", it.label)) }
    })
    override suspend fun importJson(payload: JSONObject) {
        val rows = payload.getJSONArray("apps")
        val records = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val name = row.getString("packageName")
            require(name.length <= 255 && name.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) { "Invalid app package in backup" }
            name to row.getString("label").take(255)
        }
        records.filter { it.first != ownPackage }.forEach { (name, label) -> repository.setLocked(name, label, true) }
        onImported()
    }
}
