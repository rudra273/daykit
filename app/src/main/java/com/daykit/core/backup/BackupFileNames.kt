package com.daykit.core.backup

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object BackupFileNames {
    /**
     * The trailing `-m` / `-a` marks a manual or automatic backup, so the source is
     * readable from the file name alone — in the Drive web UI, or after the
     * `appProperties` metadata is lost. The group is optional: names written before
     * the marker existed are still in users' Drive folders and must keep parsing.
     */
    private val driveBackupRegex =
        Regex("""daykit-backup-v(\d+)-(\d{8})T(\d{6})Z(?:-([ma]))?\.daykit""")
    private val driveFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")
        .withZone(ZoneOffset.UTC)
    private val displayFormatter = DateTimeFormatter.ofPattern("dd MMM, h:mm a", Locale.ENGLISH)
        .withZone(ZoneId.of("Asia/Kolkata"))

    fun backupName(
        payloadVersion: Int = DayKitBackupService.PAYLOAD_VERSION,
        createdAt: Instant = Instant.now(),
        source: DriveBackupSource = DriveBackupSource.Manual,
    ): String {
        return "daykit-backup-v$payloadVersion-${driveFormatter.format(createdAt)}-" +
            "${source.nameMarker}.daykit"
    }

    fun parse(name: String): BackupFileNameInfo? {
        driveBackupRegex.matchEntire(name)?.let { match ->
            val instant = parseUtc(match.groupValues[2], match.groupValues[3])
            return BackupFileNameInfo(
                version = match.groupValues[1].toInt(),
                exportedAtMillis = instant.toEpochMilli(),
                exportedAt = displayFormatter.format(instant),
                // Absent on pre-marker names; the caller falls back to appProperties.
                source = DriveBackupSource.fromNameMarker(match.groupValues[4]),
            )
        }

        return null
    }

    fun displayDate(millis: Long): String {
        return displayFormatter.format(Instant.ofEpochMilli(millis))
    }

    private fun parseUtc(date: String, time: String): Instant {
        val text = "${date}T${time}Z"
        return Instant.from(driveFormatter.parse(text))
    }
}

data class BackupFileNameInfo(
    val version: Int,
    val exportedAtMillis: Long,
    val exportedAt: String,
    /** Null when the name carries no `-m`/`-a` marker (written before it existed). */
    val source: DriveBackupSource?,
)
