package com.daykit.feature.devicemanager.data

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import com.daykit.core.permissions.AppLockPermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInsight(
    val name: String,
    val packageName: String,
    val lastUsed: Long,
    val bytes: Long?,
    val foregroundMillis7d: Long,
)
data class FileInsight(
    val name: String,
    val bytes: Long,
    val path: String,
    /** A document URI within the folder the user explicitly selected. */
    val uri: Uri,
)
data class DeviceSnapshot(
    val usageAccess: Boolean,
    val leastUsed: List<AppInsight>,
    val largestApps: List<AppInsight>,
    val mostUsed: List<AppInsight>,
    val batteryLevel: Int?,
    val batteryHealth: String,
    val charging: Boolean,
    val storageTotal: Long,
    val storageFree: Long,
)

object DeviceReader {
    suspend fun load(context: Context): DeviceSnapshot = withContext(Dispatchers.IO) {
        val usageAccess = AppLockPermissionChecker.hasUsageAccess(context)
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val now = System.currentTimeMillis()
        // UsageStats retention varies by device. A 90-day window is a useful ranking,
        // but "never seen" also includes apps with no retained usage data.
        val usage = if (usageAccess) {
            (context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
                .queryAndAggregateUsageStats(now - 90L * 24 * 60 * 60 * 1000, now)
        } else emptyMap()
        val weekUsage = if (usageAccess) {
            (context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
                .queryAndAggregateUsageStats(now - 7L * 24 * 60 * 60 * 1000, now)
        } else emptyMap()
        val storage = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val apps = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val bytes = if (usageAccess) runCatching {
                    val stats = storage.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, android.os.Process.myUserHandle())
                    stats.appBytes + stats.dataBytes + stats.cacheBytes
                }.getOrNull() else null
                AppInsight(
                    info.loadLabel(pm).toString(), pkg, usage[pkg]?.lastTimeUsed ?: 0L,
                    bytes, weekUsage[pkg]?.totalTimeInForeground ?: 0L,
                )
            }
        val battery = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val health = when (battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure reported"
            else -> "Unavailable"
        }
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val disk = android.os.Environment.getDataDirectory()
        DeviceSnapshot(
            usageAccess = usageAccess,
            leastUsed = if (usageAccess) apps.sortedWith(compareBy<AppInsight> { it.lastUsed }.thenBy { it.name }) else emptyList(),
            largestApps = apps.filter { it.bytes != null }.sortedByDescending { it.bytes },
            mostUsed = if (usageAccess) apps.filter { it.foregroundMillis7d > 0 }.sortedByDescending { it.foregroundMillis7d }.take(10) else emptyList(),
            batteryLevel = if (level >= 0 && scale > 0) level * 100 / scale else null,
            batteryHealth = health,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            storageTotal = disk.totalSpace,
            storageFree = disk.freeSpace,
        )
    }

    suspend fun largestFiles(context: Context, tree: android.net.Uri): List<FileInsight> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val root = DocumentsContract.getTreeDocumentId(tree)
        val pending = ArrayDeque<Pair<String, String>>()
        pending.add(root to "")
        val found = mutableListOf<FileInsight>()
        var visited = 0
        while (pending.isNotEmpty() && visited < 10_000) {
            val (parent, path) = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            runCatching {
                resolver.query(children, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext() && visited < 10_000) {
                        visited++
                        val id = cursor.getString(0) ?: continue
                        val name = cursor.getString(1) ?: id
                        val type = cursor.getString(2)
                        val childPath = if (path.isEmpty()) name else "$path/$name"
                        if (type == DocumentsContract.Document.MIME_TYPE_DIR) pending.add(id to childPath)
                        else if (!cursor.isNull(3)) {
                            found.add(
                                FileInsight(
                                    name = name,
                                    bytes = cursor.getLong(3),
                                    path = childPath,
                                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                                ),
                            )
                        }
                    }
                }
            }
        }
        found.sortedByDescending { it.bytes }.take(10)
    }
}
