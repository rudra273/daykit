package com.daykit.feature.applock.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class LockedPackageCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isInitialized(): Boolean {
        return prefs.getBoolean(KEY_INITIALIZED, false)
    }

    fun getRecords(): List<LockedPackageRecord>? {
        if (!isInitialized()) return null
        val rawRecords = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(rawRecords)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                LockedPackageRecord(
                    packageName = item.getString(KEY_PACKAGE_NAME),
                    label = item.optString(KEY_LABEL, item.getString(KEY_PACKAGE_NAME)),
                    updatedAtMillis = item.optLong(KEY_UPDATED_AT, 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun putRecords(records: List<LockedPackageRecord>) {
        val array = JSONArray()
        records
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .forEach { record ->
                array.put(
                    JSONObject()
                        .put(KEY_PACKAGE_NAME, record.packageName)
                        .put(KEY_LABEL, record.label)
                        .put(KEY_UPDATED_AT, record.updatedAtMillis),
                )
            }

        prefs.edit(commit = true) {
            putBoolean(KEY_INITIALIZED, true)
            putString(KEY_RECORDS, array.toString())
            putStringSet(KEY_PACKAGES, records.map { it.packageName }.toSet())
        }
    }

    fun getPackages(): Set<String>? {
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) return null
        return prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty()
    }

    fun putPackages(packageNames: Set<String>) {
        val labelsByPackage = getRecords().orEmpty().associateBy { it.packageName }
        putRecords(
            packageNames.map { packageName ->
                labelsByPackage[packageName]
                    ?: LockedPackageRecord(
                        packageName = packageName,
                        label = packageName,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
            },
        )
    }

    fun updatePackage(packageName: String, label: String, locked: Boolean): List<LockedPackageRecord> {
        val current = getRecords().orEmpty()
            .filterNot { it.packageName == packageName }
            .toMutableList()
        if (locked) {
            current.add(
                LockedPackageRecord(
                    packageName = packageName,
                    label = label,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        } else {
            current.removeAll { it.packageName == packageName }
        }
        putRecords(current)
        return getRecords().orEmpty()
    }

    private companion object {
        const val PREFS_NAME = "app_lock_package_cache"
        const val KEY_INITIALIZED = "initialized"
        const val KEY_PACKAGES = "packages"
        const val KEY_RECORDS = "records"
        const val KEY_PACKAGE_NAME = "packageName"
        const val KEY_LABEL = "label"
        const val KEY_UPDATED_AT = "updatedAtMillis"
    }
}

data class LockedPackageRecord(
    val packageName: String,
    val label: String,
    val updatedAtMillis: Long,
)
