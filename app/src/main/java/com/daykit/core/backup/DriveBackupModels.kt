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
    fun backupsToDelete(backups: List<DriveBackupFile>, retainCount: Int): List<DriveBackupFile> {
        if (retainCount <= 0) return backups
        return backups.sortedWith(
            compareByDescending<DriveBackupFile> { it.createdAtMillis }
                .thenByDescending { it.name },
        ).drop(retainCount)
    }
}
