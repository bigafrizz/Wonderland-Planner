package com.wonderland.taschasplanner.data.backup

import android.content.Context
import com.wonderland.taschasplanner.data.local.WonderlandDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Pure file-level helpers for atomic JSON snapshot persistence and recovery.
 * Guarded so an empty database can never overwrite a valid non-empty backup.
 */
object AutoBackupFileHelper {
    const val AUTO_BACKUP_FILENAME = "wonderland_auto_backup.json"

    fun isSnapshotNonEmpty(snapshot: WonderlandBackupSnapshot): Boolean {
        return snapshot.buildings.isNotEmpty() ||
            snapshot.contacts.isNotEmpty() ||
            snapshot.tasks.isNotEmpty() ||
            snapshot.shifts.isNotEmpty() ||
            snapshot.compTimeEntries.isNotEmpty() ||
            snapshot.noteTodos.isNotEmpty()
    }

    fun writeAtomicJson(targetFile: File, json: String): Boolean {
        return runCatching {
            val parent = targetFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val tmpFile = File(parent ?: targetFile.absoluteFile.parentFile, "${targetFile.name}.tmp")
            FileOutputStream(tmpFile).use { fos ->
                fos.write(json.toByteArray(Charsets.UTF_8))
                fos.flush()
                runCatching { fos.fd.sync() }
            }
            if (!tmpFile.renameTo(targetFile)) {
                // Fallback for filesystems where renameTo does not overwrite existing files
                tmpFile.copyTo(targetFile, overwrite = true)
                tmpFile.delete()
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Writes [snapshot] atomically to [primaryFile] and optional [mirrorFile].
     * Returns false without modifying existing files if [snapshot] is empty.
     */
    fun writeSnapshotToFiles(
        snapshot: WonderlandBackupSnapshot,
        primaryFile: File,
        mirrorFile: File?
    ): Boolean {
        if (!isSnapshotNonEmpty(snapshot)) return false
        val encoded = WonderlandBackupCodec.encode(snapshot)
        val primaryOk = writeAtomicJson(primaryFile, encoded)
        val mirrorOk = mirrorFile?.let { writeAtomicJson(it, encoded) } ?: false
        return primaryOk || mirrorOk
    }

    /**
     * Reads [primaryFile] and optional [mirrorFile], returning the newest valid non-empty
     * snapshot (by exportedAtMillis) along with its raw JSON.
     */
    fun readLatestValidSnapshot(
        primaryFile: File,
        mirrorFile: File?
    ): Pair<String, WonderlandBackupSnapshot>? {
        val candidates = listOfNotNull(primaryFile, mirrorFile)
            .filter { it.exists() && it.isFile && it.length() > 0L }
            .mapNotNull { file ->
                runCatching {
                    val json = file.readText(Charsets.UTF_8)
                    val decoded = WonderlandBackupCodec.decode(json)
                    if (isSnapshotNonEmpty(decoded)) json to decoded else null
                }.getOrNull()
            }
        return candidates.maxByOrNull { it.second.exportedAtMillis }
    }
}

/**
 * Automatic local rolling backup & self-healing startup recovery store.
 * Writes snapshots atomically to both internal app storage and app-specific external storage.
 */
class AutoBackupStore(
    private val context: Context,
    private val db: WonderlandDatabase,
    private val backupManager: WonderlandBackupManager,
    private val appScope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("wonderland_prefs", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var pendingJob: Job? = null

    private val _lastBackupAtMillis = MutableStateFlow(
        prefs.getLong(KEY_LAST_AUTO_BACKUP_MILLIS, 0L).takeIf { it > 0L }
    )
    val lastBackupAtMillis: StateFlow<Long?> = _lastBackupAtMillis.asStateFlow()

    private fun primaryFile(): File = File(context.filesDir, AutoBackupFileHelper.AUTO_BACKUP_FILENAME)

    private fun mirrorFile(): File? =
        context.getExternalFilesDir(null)?.let { File(it, AutoBackupFileHelper.AUTO_BACKUP_FILENAME) }

    fun hasAnyBackupFile(): Boolean {
        val p = primaryFile()
        val m = mirrorFile()
        return (p.exists() && p.length() > 0L) || (m != null && m.exists() && m.length() > 0L)
    }

    /**
     * Schedules a debounced snapshot so rapid bulk writes coalesce cleanly.
     */
    fun scheduleSnapshot(debounceMs: Long = 1_200L) {
        pendingJob?.cancel()
        pendingJob = appScope.launch {
            delay(debounceMs)
            snapshotNow()
        }
    }

    /**
     * Immediately captures a non-empty snapshot of the database onto disk.
     */
    suspend fun snapshotNow(): Boolean = withContext(appScope.coroutineContext + NonCancellable) {
        mutex.withLock {
            pendingJob?.cancel()
            pendingJob = null
            val snapshot = WonderlandBackupSnapshot(
                exportedAtMillis = System.currentTimeMillis(),
                buildings = db.buildingDao().getAllBuildingsOnce(),
                contacts = db.contactDao().getAllContactsOnce(),
                tasks = db.taskDao().getAllTasksOnce(),
                shifts = db.shiftScheduleDao().getAllShiftsOnce(),
                compTimeEntries = db.compTimeDao().getAllEntriesOnce(),
                noteTodos = db.noteTodoDao().getAllNotesOnce()
            )
            val saved = AutoBackupFileHelper.writeSnapshotToFiles(
                snapshot = snapshot,
                primaryFile = primaryFile(),
                mirrorFile = mirrorFile()
            )
            if (saved) {
                _lastBackupAtMillis.value = snapshot.exportedAtMillis
                prefs.edit().putLong(KEY_LAST_AUTO_BACKUP_MILLIS, snapshot.exportedAtMillis).apply()
            }
            saved
        }
    }

    /**
     * Checks whether all database tables are empty. If empty and the user did not intentionally
     * clear data, automatically restores the latest valid safety snapshot.
     */
    suspend fun tryAutoRecoverIfEmpty(userExplicitlyCleared: Boolean): WonderlandBackupSnapshot? =
        withContext(appScope.coroutineContext + NonCancellable) {
            mutex.withLock {
                val isEmpty = db.buildingDao().count() == 0 &&
                    db.contactDao().count() == 0 &&
                    db.taskDao().count() == 0 &&
                    db.shiftScheduleDao().count() == 0 &&
                    db.compTimeDao().count() == 0 &&
                    db.noteTodoDao().count() == 0

                if (!isEmpty || userExplicitlyCleared) return@withLock null

                val latest = AutoBackupFileHelper.readLatestValidSnapshot(
                    primaryFile = primaryFile(),
                    mirrorFile = mirrorFile()
                ) ?: return@withLock null

                runCatching {
                    backupManager.restoreFromJson(latest.first)
                }.getOrNull()
            }
        }

    /**
     * Deletes local safety backups only when the user explicitly confirms "Clear All Data".
     */
    suspend fun clearBackups() = withContext(appScope.coroutineContext + NonCancellable) {
        mutex.withLock {
            pendingJob?.cancel()
            pendingJob = null
            runCatching { primaryFile().delete() }
            runCatching { mirrorFile()?.delete() }
            _lastBackupAtMillis.value = null
            prefs.edit().remove(KEY_LAST_AUTO_BACKUP_MILLIS).apply()
        }
    }

    companion object {
        private const val KEY_LAST_AUTO_BACKUP_MILLIS = "last_auto_backup_millis"
    }
}
