package com.rudra.daykit.core.backup

import org.json.JSONObject

class DayKitBackupService(
    private val crypto: BackupCrypto,
    private val contributors: List<BackupContributor>,
) {
    suspend fun exportEncrypted(
        password: CharArray,
        includedToolKeys: Set<String>? = null,
    ): String {
        val tools = JSONObject()
        contributors
            .filter { contributor -> includedToolKeys == null || contributor.toolKey in includedToolKeys }
            .forEach { contributor ->
            tools.put(
                contributor.toolKey,
                JSONObject()
                    .put("schemaVersion", contributor.schemaVersion)
                    .put("payload", contributor.exportJson()),
            )
        }

        val payload = JSONObject()
            .put("app", "DayKit")
            .put("payloadVersion", PAYLOAD_VERSION)
            .put("exportedAtMillis", System.currentTimeMillis())
            .put("tools", tools)

        return crypto.encrypt(payload, password).toString()
    }

    suspend fun importEncrypted(encryptedBackup: String, password: CharArray) {
        val payload = crypto.decrypt(JSONObject(encryptedBackup), password)
        require(payload.getInt("payloadVersion") == PAYLOAD_VERSION) { "Unsupported backup payload" }

        val tools = payload.getJSONObject("tools")
        contributors.forEach { contributor ->
            if (tools.has(contributor.toolKey)) {
                val section = tools.getJSONObject(contributor.toolKey)
                if (section.getInt("schemaVersion") == contributor.schemaVersion) {
                    contributor.importJson(section.getJSONObject("payload"))
                }
            }
        }
    }

    companion object {
        const val PAYLOAD_VERSION = 1
    }
}
