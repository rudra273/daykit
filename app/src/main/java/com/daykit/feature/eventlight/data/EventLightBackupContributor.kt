package com.daykit.feature.eventlight.data

import android.content.Context
import com.daykit.core.backup.BackupContributor
import com.daykit.core.backup.BackupToolKeys
import org.json.JSONObject

class EventLightBackupContributor(private val context: Context) : BackupContributor {
    override val toolKey = BackupToolKeys.EVENT_LIGHT
    override val schemaVersion = 2

    override suspend fun exportJson(): JSONObject {
        val s = EventLightStore.get(context)
        return JSONObject()
            .put("thicknessDp", s.thicknessDp.toDouble())
            .put("colorTemp", s.colorTemp.toDouble())
    }

    override suspend fun importJson(payload: JSONObject) {
        // Parse everything before writing, and never start an overlay as a side effect of restore.
        val thickness = payload.getDouble("thicknessDp").toFloat()
        val colorTemp = payload.getDouble("colorTemp").toFloat()
        require(
            thickness.isFinite() &&
                thickness in EVENT_LIGHT_MIN_THICKNESS_DP..EVENT_LIGHT_MAX_THICKNESS_DP &&
                colorTemp.isFinite() &&
                colorTemp in 0f..1f,
        ) { "Invalid Event Light settings" }
        EventLightStore.setThickness(context, thickness)
        EventLightStore.setColorTemp(context, colorTemp)
    }
}
