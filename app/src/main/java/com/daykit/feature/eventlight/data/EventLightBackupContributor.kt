package com.daykit.feature.eventlight.data

import android.content.Context
import com.daykit.core.backup.BackupContributor
import com.daykit.core.backup.BackupToolKeys
import org.json.JSONObject

class EventLightBackupContributor(private val context: Context) : BackupContributor {
    override val toolKey = BackupToolKeys.EVENT_LIGHT
    override val schemaVersion = 1
    override suspend fun exportJson(): JSONObject {
        val s = EventLightStore.get(context)
        return JSONObject().put("colorArgb", s.colorArgb).put("thicknessDp", s.thicknessDp.toDouble())
            .put("brightness", s.brightness.toDouble()).put("opacity", s.opacity.toDouble())
            .put("top", s.topEnabled).put("bottom", s.bottomEnabled).put("left", s.leftEnabled).put("right", s.rightEnabled)
    }
    override suspend fun importJson(payload: JSONObject) {
        // Parse everything before writing, and never start an overlay as a side effect of restore.
        val color = payload.getInt("colorArgb")
        val thickness = payload.getDouble("thicknessDp").toFloat()
        val brightness = payload.getDouble("brightness").toFloat()
        val opacity = payload.getDouble("opacity").toFloat()
        require(thickness.isFinite() && thickness in 1f..120f && brightness in 0f..1f && opacity in 0f..1f) { "Invalid Event Light settings" }
        val top = payload.getBoolean("top")
        val bottom = payload.getBoolean("bottom")
        val left = payload.getBoolean("left")
        val right = payload.getBoolean("right")
        EventLightStore.setColor(context, color)
        EventLightStore.setThickness(context, thickness)
        EventLightStore.setBrightness(context, brightness)
        EventLightStore.setOpacity(context, opacity)
        EventLightStore.setTopEnabled(context, top)
        EventLightStore.setBottomEnabled(context, bottom)
        EventLightStore.setLeftEnabled(context, left)
        EventLightStore.setRightEnabled(context, right)
    }
}
