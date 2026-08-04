package com.daykit.feature.focus.data

import com.daykit.core.backup.BackupContributor
import com.daykit.core.backup.BackupToolKeys
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backs up active focus blocks. Without this a device restore silently dropped
 * every running block, handing back the apps the user had deliberately locked
 * themselves out of.
 *
 * Expiry is stored as an absolute timestamp, so a restored block resumes with
 * whatever time was left rather than restarting. Blocks that expired while the
 * backup sat around are dropped on import — see [FocusBlockStore.mergeBlocks].
 *
 * Note the enforcement caveat: a restored block only actually blocks if the new
 * device has granted Usage Access to DayKit.
 */
class FocusBackupContributor(
    private val focusBlockStore: FocusBlockStore,
) : BackupContributor {
    override val toolKey: String = BackupToolKeys.FOCUS
    override val schemaVersion: Int = 1

    override suspend fun exportJson(): JSONObject {
        return JSONObject().put(
            "blocks",
            JSONArray().also { rows ->
                focusBlockStore.getActiveBlocks().forEach { block ->
                    rows.put(
                        JSONObject()
                            .put("packageName", block.packageName)
                            .put("label", block.label)
                            .put("lockUntilMillis", block.lockUntilMillis),
                    )
                }
            },
        )
    }

    override suspend fun importJson(payload: JSONObject) {
        val rows = payload.optJSONArray("blocks") ?: return
        val blocks = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val packageName = row.optString("packageName")
                if (packageName.isEmpty()) continue
                add(
                    FocusBlock(
                        packageName = packageName,
                        label = row.optString("label", packageName),
                        lockUntilMillis = row.optLong("lockUntilMillis", 0L),
                    ),
                )
            }
        }
        focusBlockStore.mergeBlocks(blocks)
    }
}
