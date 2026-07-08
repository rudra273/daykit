package com.rudra.daykit.core.backup

import org.json.JSONObject

interface BackupContributor {
    val toolKey: String
    val schemaVersion: Int

    suspend fun exportJson(): JSONObject

    suspend fun importJson(payload: JSONObject)
}
