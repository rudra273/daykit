package com.daykit.core.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.Instant
import java.util.UUID

class GoogleDriveBackupClient {
    suspend fun listBackups(accessToken: String): List<DriveBackupFile> {
        val folderId = findBackupFolder(accessToken) ?: return emptyList()
        val query = "'$folderId' in parents and trashed = false and " +
            "mimeType = '$BACKUP_MIME_TYPE' and " +
            "appProperties has { key='app' and value='$APP_PROPERTY_VALUE' } and " +
            "appProperties has { key='type' and value='$TYPE_PROPERTY_VALUE' }"
        val fields = "files(id,name,createdTime,modifiedTime,size,appProperties)"
        val url = URL(
            "$DRIVE_FILES_URL?q=${query.urlEncoded()}&fields=${fields.urlEncoded()}&pageSize=100",
        )
        val response = url.openDriveConnection(accessToken, "GET").readJson()
        val files = response.optJSONArray("files") ?: JSONArray()
        return buildList {
            for (index in 0 until files.length()) {
                files.optJSONObject(index)?.toDriveBackupFile()?.let(::add)
            }
        }.sortedByDescending { it.createdAtMillis }
    }

    suspend fun uploadBackup(
        accessToken: String,
        encryptedBackup: String,
        source: DriveBackupSource,
        createdAt: Instant = Instant.now(),
        payloadVersion: Int = DayKitBackupService.PAYLOAD_VERSION,
        retainCount: Int = RETAIN_BACKUP_COUNT,
    ): DriveUploadResult {
        val folderId = findBackupFolder(accessToken) ?: createBackupFolder(accessToken)
        val fileName = BackupFileNames.backupName(payloadVersion = payloadVersion, createdAt = createdAt)
        val file = createBackupFile(
            accessToken = accessToken,
            folderId = folderId,
            fileName = fileName,
            encryptedBackup = encryptedBackup,
            source = source,
            createdAtMillis = createdAt.toEpochMilli(),
            payloadVersion = payloadVersion,
        )
        val deletedCount = pruneBackups(accessToken = accessToken, retainCount = retainCount)
        return DriveUploadResult(file = file, deletedOldBackups = deletedCount)
    }

    suspend fun downloadBackup(accessToken: String, fileId: String): String {
        return URL("$DRIVE_FILES_URL/${fileId.pathEncoded()}?alt=media")
            .openDriveConnection(accessToken, "GET")
            .readTextBody()
    }

    suspend fun deleteBackup(accessToken: String, fileId: String) {
        URL("$DRIVE_FILES_URL/${fileId.pathEncoded()}")
            .openDriveConnection(accessToken, "DELETE")
            .readTextBody()
    }

    suspend fun pruneBackups(
        accessToken: String,
        retainCount: Int = RETAIN_BACKUP_COUNT,
    ): Int {
        val oldBackups = DriveBackupRetention.backupsToDelete(listBackups(accessToken), retainCount)
        oldBackups.forEach { deleteBackup(accessToken, it.id) }
        return oldBackups.size
    }

    private fun findBackupFolder(accessToken: String): String? {
        val query = "mimeType = '$DRIVE_FOLDER_MIME_TYPE' and name = '$BACKUP_FOLDER_NAME' and trashed = false"
        val fields = "files(id,name)"
        val url = URL(
            "$DRIVE_FILES_URL?q=${query.urlEncoded()}&fields=${fields.urlEncoded()}&pageSize=1",
        )
        val response = url.openDriveConnection(accessToken, "GET").readJson()
        val files = response.optJSONArray("files") ?: JSONArray()
        return files.optJSONObject(0)?.optString("id")?.takeIf(String::isNotBlank)
    }

    private fun createBackupFolder(accessToken: String): String {
        val metadata = JSONObject()
            .put("name", BACKUP_FOLDER_NAME)
            .put("mimeType", DRIVE_FOLDER_MIME_TYPE)
        val response = URL(DRIVE_FILES_URL)
            .openDriveConnection(accessToken, "POST", "application/json; charset=utf-8")
            .writeJson(metadata)
            .readJson()
        return response.getString("id")
    }

    private fun createBackupFile(
        accessToken: String,
        folderId: String,
        fileName: String,
        encryptedBackup: String,
        source: DriveBackupSource,
        createdAtMillis: Long,
        payloadVersion: Int,
    ): DriveBackupFile {
        val boundary = "daykit-${UUID.randomUUID()}"
        val appProperties = JSONObject()
            .put("app", APP_PROPERTY_VALUE)
            .put("type", TYPE_PROPERTY_VALUE)
            .put("payloadVersion", payloadVersion.toString())
            .put("source", source.value)
            .put("createdAtMillis", createdAtMillis.toString())
        val metadata = JSONObject()
            .put("name", fileName)
            .put("mimeType", BACKUP_MIME_TYPE)
            .put("parents", JSONArray().put(folderId))
            .put("appProperties", appProperties)

        val connection = URL("$DRIVE_UPLOAD_URL?uploadType=multipart&fields=${FILE_FIELDS.urlEncoded()}")
            .openDriveConnection(accessToken, "POST", "multipart/related; boundary=$boundary")
        connection.doOutput = true
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.append("--").append(boundary).append("\r\n")
            writer.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            writer.append(metadata.toString()).append("\r\n")
            writer.append("--").append(boundary).append("\r\n")
            writer.append("Content-Type: ").append(BACKUP_MIME_TYPE).append("\r\n\r\n")
            writer.append(encryptedBackup).append("\r\n")
            writer.append("--").append(boundary).append("--")
        }
        return connection.readJson().toDriveBackupFile()
            ?: DriveBackupFile(
                id = "",
                name = fileName,
                createdAtMillis = createdAtMillis,
                payloadVersion = payloadVersion,
                source = source,
                sizeBytes = null,
            )
    }

    private fun JSONObject.toDriveBackupFile(): DriveBackupFile? {
        val id = optString("id").takeIf(String::isNotBlank) ?: return null
        val name = optString("name").takeIf(String::isNotBlank) ?: return null
        val appProperties = optJSONObject("appProperties")
        val parsedName = BackupFileNames.parse(name)
        val createdAtMillis = appProperties
            ?.optString("createdAtMillis")
            ?.toLongOrNull()
            ?: parsedName?.exportedAtMillis
            ?: optString("createdTime").toInstantMillisOrNull()
            ?: optString("modifiedTime").toInstantMillisOrNull()
            ?: 0L
        val payloadVersion = appProperties
            ?.optString("payloadVersion")
            ?.toIntOrNull()
            ?: parsedName?.version
            ?: DayKitBackupService.PAYLOAD_VERSION
        val source = DriveBackupSource.fromValue(appProperties?.optString("source"))
        val sizeBytes = optString("size").toLongOrNull()
        return DriveBackupFile(
            id = id,
            name = name,
            createdAtMillis = createdAtMillis,
            payloadVersion = payloadVersion,
            source = source,
            sizeBytes = sizeBytes,
        )
    }

    private fun URL.openDriveConnection(
        accessToken: String,
        method: String,
        contentType: String? = null,
    ): HttpURLConnection {
        return (openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            contentType?.let { setRequestProperty("Content-Type", it) }
        }
    }

    private fun HttpURLConnection.writeJson(json: JSONObject): HttpURLConnection {
        doOutput = true
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            writer.write(json.toString())
        }
        return this
    }

    private fun HttpURLConnection.readJson(): JSONObject {
        val body = readTextBody()
        return JSONObject(body.ifBlank { "{}" })
    }

    private fun HttpURLConnection.readTextBody(): String {
        val code = responseCode
        val body = if (code in 200..299) {
            inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } else {
            errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        if (code !in 200..299) {
            error("Drive request failed ($code): ${body.ifBlank { responseMessage }}")
        }
        return body
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

    private fun String.pathEncoded(): String = URLEncoder.encode(this, "UTF-8")

    private fun String.toInstantMillisOrNull(): Long? {
        return runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
    }

    companion object {
        const val RETAIN_BACKUP_COUNT = 3
        const val BACKUP_FOLDER_NAME = "DayKit Backups"
        const val BACKUP_MIME_TYPE = "application/vnd.daykit.backup+json"
        private const val APP_PROPERTY_VALUE = "DayKit"
        private const val TYPE_PROPERTY_VALUE = "backup"
        private const val DRIVE_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val FILE_FIELDS = "id,name,createdTime,modifiedTime,size,appProperties"
    }
}
