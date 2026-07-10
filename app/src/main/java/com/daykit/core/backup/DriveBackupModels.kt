package com.daykit.core.backup

enum class DriveBackupSource(val value: String, val label: String) {
    Manual("manual", "Manual"),
    Automatic("automatic", "Automatic");

    companion object {
        fun fromValue(value: String?): DriveBackupSource {
            return entries.firstOrNull { it.value == value } ?: Manual
        }
    }
}

enum class DriveBackupSchedule(val value: String, val label: String) {
    Off("off", "Off"),
    Daily("daily", "Daily"),
    Weekly("weekly", "Weekly"),
    Manual("manual", "Only when I tap 'Back up now'");

    companion object {
        fun fromValue(value: String?): DriveBackupSchedule {
            return entries.firstOrNull { it.value == value } ?: Weekly
        }
    }
}

data class DriveBackupFile(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val payloadVersion: Int,
    val source: DriveBackupSource,
    val sizeBytes: Long?,
) {
    val createdAtDisplay: String
        get() = BackupFileNames.displayDate(createdAtMillis)
}

data class DriveUploadResult(
    val file: DriveBackupFile,
    val deletedOldBackups: Int,
)

object DriveBackupRetention {
    /**
     * Selects the backups to delete, keeping the newest [retainCount].
     *
     * The most recent Manual backup is always protected, even if it falls
     * outside the retain window: automatic backups never contain the sensitive
     * tools (vault, key store, secure notes) because the background worker has
     * no PIN-derived key, so a Manual backup is the only Drive copy of that
     * data. Rotating it away would silently destroy the only cloud backup of it.
     */
    fun backupsToDelete(backups: List<DriveBackupFile>, retainCount: Int): List<DriveBackupFile> {
        if (retainCount <= 0) return backups
        val ordered = backups.sortedWith(
            compareByDescending<DriveBackupFile> { it.createdAtMillis }
                .thenByDescending { it.name },
        )
        val newestManual = ordered.firstOrNull { it.source == DriveBackupSource.Manual }
        return ordered.drop(retainCount).filter { it.id != newestManual?.id }
    }
}
