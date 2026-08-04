package com.daykit.core.backup

/**
 * How a backup was triggered. [nameMarker] is the single letter appended to the file
 * name (see [BackupFileNames]) so the source survives even without `appProperties`.
 */
enum class DriveBackupSource(val value: String, val label: String, val nameMarker: String) {
    Manual("manual", "Manual", "m"),

    /**
     * A scheduled backup. Runs in the foreground on unlock (see [DriveBackupRunner]),
     * never in the background — so it holds the PIN-derived key and is just as
     * complete as a [Manual] one.
     */
    Automatic("automatic", "Automatic", "a");

    companion object {
        fun fromValue(value: String?): DriveBackupSource {
            return entries.firstOrNull { it.value == value } ?: Manual
        }

        /** Null for a blank/unknown marker, so callers can fall back to metadata. */
        fun fromNameMarker(marker: String?): DriveBackupSource? {
            if (marker.isNullOrBlank()) return null
            return entries.firstOrNull { it.nameMarker == marker }
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
     * Every backup is created with the PIN-derived key available — manual ones from
     * the button, automatic ones from [DriveBackupRunner] on unlock — so they all
     * contain the same tools and none needs protecting from rotation. (An earlier
     * background worker produced partial backups and did need that special case.)
     */
    fun backupsToDelete(backups: List<DriveBackupFile>, retainCount: Int): List<DriveBackupFile> {
        if (retainCount <= 0) return backups
        return backups
            .sortedWith(
                compareByDescending<DriveBackupFile> { it.createdAtMillis }
                    .thenByDescending { it.name },
            )
            .drop(retainCount)
    }
}
