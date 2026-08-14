package com.example.workouttracker.data.backup

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

// Requirements for uploading and downloading the workout database in Google Drive
interface GoogleDriveGateway {
    suspend fun uploadOrReplace(localFile: File, folderName: String, remoteName: String): String

    suspend fun downloadLatest(folderName: String, remoteName: String, destination: File): File
}

// Error used when a Drive request shows that authorization has expired
class InvalidDriveAuthorizationException(message: String) : IllegalStateException(message)

// Use the Drive REST API to access only the folder and files created by this app
class GoogleDriveRestGateway(
    private val authorization: GoogleAuthorizationGateway,
) : GoogleDriveGateway {
    // Upload a checkpoint by creating or replacing the named Drive file
    override suspend fun uploadOrReplace(localFile: File, folderName: String, remoteName: String): String =
        withContext(Dispatchers.IO) {
            require(localFile.isFile) { "The database checkpoint does not exist." }
            val token = authorization.accessToken()
            val folderId = findFolder(token, folderName) ?: createFolder(token, folderName)
            val fileId = findLatestFile(token, folderId, remoteName)
                ?: createFile(token, folderId, remoteName)
            openAuthorized(
                "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media",
                token,
                "PATCH",
            ).useConnection { connection ->
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", SQLITE_MIME_TYPE)
                connection.outputStream.use { output -> localFile.inputStream().use { it.copyTo(output) } }
                connection.requireSuccess()
            }
            fileId
        }

    // Download the newest matching backup into a local file
    override suspend fun downloadLatest(
        folderName: String,
        remoteName: String,
        destination: File,
    ): File = withContext(Dispatchers.IO) {
        val token = authorization.accessToken()
        val folderId = requireNotNull(findFolder(token, folderName)) {
            "No Workout Tracker backup folder was found."
        }
        val fileId = requireNotNull(findLatestFile(token, folderId, remoteName)) {
            "No workout backup was found."
        }
        openAuthorized(
            "https://www.googleapis.com/drive/v3/files/$fileId?alt=media",
            token,
            "GET",
        ).useConnection { connection ->
            connection.requireSuccess()
            destination.parentFile?.mkdirs()
            connection.inputStream.use { input -> destination.outputStream().use(input::copyTo) }
        }
        destination
    }

    // Find the app's backup folder by name
    private fun findFolder(token: String, folderName: String): String? {
        val query = "name = '${folderName.escapeQuery()}' and " +
            "mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        return listFirstId(token, query)
    }

    // Find the most recently modified matching file inside the backup folder
    private fun findLatestFile(token: String, folderId: String, remoteName: String): String? {
        val query = "name = '${remoteName.escapeQuery()}' and trashed = false and " +
            "'$folderId' in parents"
        return listFirstId(token, query)
    }

    // Return the first Drive file ID matching a query
    private fun listFirstId(token: String, query: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=drive&orderBy=modifiedTime%20desc&pageSize=1" +
            "&fields=files(id)&q=${URLEncoder.encode(query, Charsets.UTF_8.name())}"
        return openAuthorized(url, token, "GET").useConnection { connection ->
            val body = connection.readSuccessText()
            JSONObject(body).getJSONArray("files").optJSONObject(0)?.getString("id")
        }
    }

    // Create the Drive folder used to store backups
    private fun createFolder(token: String, folderName: String): String {
        val metadata = JSONObject()
            .put("name", folderName)
            .put("mimeType", "application/vnd.google-apps.folder")
        return createMetadata(token, metadata)
    }

    // Create an empty Drive file which can receive the database contents
    private fun createFile(token: String, folderId: String, remoteName: String): String {
        val metadata = JSONObject()
            .put("name", remoteName)
            .put("parents", org.json.JSONArray().put(folderId))
            .put("mimeType", SQLITE_MIME_TYPE)
        return createMetadata(token, metadata)
    }

    // Send file or folder metadata and return the created Drive ID
    private fun createMetadata(token: String, metadata: JSONObject): String {
        return openAuthorized(
            "https://www.googleapis.com/drive/v3/files?fields=id",
            token,
            "POST",
        ).useConnection { connection ->
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.bufferedWriter().use { it.write(metadata.toString()) }
            JSONObject(connection.readSuccessText()).getString("id")
        }
    }

    // Open an HTTP connection containing the current Drive authorization token
    private fun openAuthorized(url: String, token: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    // Throw a useful error when Drive does not return a successful status
    private fun HttpURLConnection.requireSuccess() {
        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw InvalidDriveAuthorizationException("Google Drive authorization expired. Sign in again.")
        }
        if (responseCode !in 200..299) {
            val details = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("Google Drive returned HTTP $responseCode${if (details.isBlank()) "." else ": $details"}")
        }
    }

    // Read a successful HTTP response as text
    private fun HttpURLConnection.readSuccessText(): String {
        requireSuccess()
        return inputStream.bufferedReader().use { it.readText() }
    }

    // Always disconnect an HTTP connection after using it
    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }

    // Escape a user-provided name before placing it inside a Drive query
    private fun String.escapeQuery(): String = replace("\\", "\\\\").replace("'", "\\'")

    private companion object {
        const val SQLITE_MIME_TYPE = "application/vnd.sqlite3"
    }
}

// Store backups in a local folder for tests and builds without Google credentials
class GoogleDriveGatewayStub(
    private val remoteRoot: File = File(System.getProperty("java.io.tmpdir"), "workout-tracker-drive"),
) : GoogleDriveGateway {
    // Copy a local checkpoint into the simulated remote folder
    override suspend fun uploadOrReplace(localFile: File, folderName: String, remoteName: String): String {
        return withContext(Dispatchers.IO) {
            require(localFile.isFile) { "The database checkpoint does not exist." }
            val folder = File(remoteRoot, folderName).apply { mkdirs() }
            require(folder.isDirectory) { "Unable to create the backup folder." }
            val destination = File(folder, remoteName)
            localFile.inputStream().use { input ->
                destination.outputStream().use(input::copyTo)
            }
            destination.absolutePath
        }
    }

    // Copy the simulated remote backup into the requested destination
    override suspend fun downloadLatest(folderName: String, remoteName: String, destination: File): File {
        return withContext(Dispatchers.IO) {
            val source = File(File(remoteRoot, folderName), remoteName)
            require(source.isFile) { "No workout backup was found." }
            destination.parentFile?.mkdirs()
            source.inputStream().use { input -> destination.outputStream().use(input::copyTo) }
            destination
        }
    }
}
