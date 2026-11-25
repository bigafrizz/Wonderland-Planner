package com.wonderland.taschasplanner.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.wonderland.taschasplanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class RemoteAppVersion(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

sealed interface UpdateCheckResult {
    data object Idle : UpdateCheckResult
    data object Checking : UpdateCheckResult
    data class UpToDate(val currentVersionName: String) : UpdateCheckResult
    data class UpdateAvailable(val remote: RemoteAppVersion) : UpdateCheckResult
    data class Downloading(val progressPercent: Int) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

class AppUpdateManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("wonderland_prefs", Context.MODE_PRIVATE)

    fun getManifestUrl(): String =
        prefs.getString(KEY_UPDATE_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_UPDATE_URL

    fun saveManifestUrl(url: String) {
        prefs.edit().putString(KEY_UPDATE_URL, url.trim()).apply()
    }

    suspend fun checkForUpdate(manifestUrl: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val trimmed = manifestUrl.trim()
        if (trimmed.isBlank()) {
            return@withContext UpdateCheckResult.Error("Enter your hosted version.json URL first.")
        }
        try {
            val conn = (URL(trimmed).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext UpdateCheckResult.Error("Server returned HTTP $code.")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val remote = RemoteAppVersion(
                versionCode = json.getInt("versionCode"),
                versionName = json.optString("versionName", "New Version"),
                apkUrl = json.getString("apkUrl"),
                releaseNotes = json.optString("releaseNotes", "")
            )
            if (remote.versionCode > BuildConfig.VERSION_CODE) {
                UpdateCheckResult.UpdateAvailable(remote)
            } else {
                UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Unable to check for updates.")
        }
    }

    suspend fun downloadApk(
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(updatesDir, "taschas-planner-update.apk")
            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            if (conn.responseCode !in 200..299) {
                return@withContext Result.failure(IllegalStateException("Download failed: HTTP ${conn.responseCode}"))
            }
            val total = conn.contentLengthLong.coerceAtLeast(1L)
            var downloaded = 0L
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(((downloaded * 100L) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun launchInPlaceInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        private const val KEY_UPDATE_URL = "update_manifest_url"
        const val DEFAULT_UPDATE_URL =
            "https://raw.githubusercontent.com/bigafrizz/Wonderland-Planner/main/version.json"
    }
}
