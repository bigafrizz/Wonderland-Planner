package com.wonderland.taschasplanner.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wonderland.taschasplanner.BuildConfig
import com.wonderland.taschasplanner.data.local.ThemeMode
import com.wonderland.taschasplanner.data.update.RemoteAppVersion
import com.wonderland.taschasplanner.data.update.UpdateCheckResult
import com.wonderland.taschasplanner.ui.WonderlandUiState
import com.wonderland.taschasplanner.ui.components.CheshireCatEmblem
import com.wonderland.taschasplanner.ui.components.OrnateGoldDivider
import com.wonderland.taschasplanner.ui.components.WonderlandCard
import com.wonderland.taschasplanner.ui.theme.LocalWonderlandColors

@Composable
fun SettingsScreen(
    state: WonderlandUiState,
    updateManifestUrl: String,
    updateState: UpdateCheckResult,
    backupStatusMessage: String?,
    onSelectTheme: (ThemeMode) -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRefreshCalendars: () -> Unit,
    onToggleCalendarVisibility: (Long, Boolean) -> Unit,
    onToggleSampleCalendarPreview: (Boolean) -> Unit,
    onUpdateManifestUrlChange: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadAndInstallUpdate: (RemoteAppVersion) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onLoadSampleData: () -> Unit,
    onClearAllData: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheshireCatEmblem(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Through the Looking Glass", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Theme, Google Calendars, Backups & Updates",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Theme Mode Card
        item {
            WonderlandCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Wonderland Appearance", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.themeMode == mode,
                                onClick = { onSelectTheme(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.displayLabel, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        // Google Calendar Integration Card
        item {
            WonderlandCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Calendar Sync", style = MaterialTheme.typography.titleLarge)
                        }
                        if (state.hasCalendarPermission) {
                            FilledTonalButton(onClick = onRefreshCalendars) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync")
                            }
                        }
                    }

                    if (!state.hasCalendarPermission) {
                        Text(
                            "Grant Calendar access to pull in existing Google Calendars synced on this Android device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onRequestCalendarPermission) {
                            Text("Connect Device Google Calendars")
                        }
                    } else if (state.calendarPrefs.isEmpty()) {
                        Text(
                            "Calendar permission granted, but no synced calendars were found on this device yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Choose which calendars appear in your Daily, Weekly & Monthly planner:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        state.calendarPrefs.forEach { cal ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(cal.colorInt))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(cal.displayName, style = MaterialTheme.typography.bodyLarge)
                                        if (cal.accountName.isNotBlank()) {
                                            Text(
                                                cal.accountName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Switch(
                                    checked = cal.isVisible,
                                    onCheckedChange = { onToggleCalendarVisibility(cal.calendarId, it) }
                                )
                            }
                        }
                    }

                    OrnateGoldDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sample Google Calendar Preview", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Shows sample calendar events when no real device events are synced",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.showSampleCalendarPreview,
                            onCheckedChange = onToggleSampleCalendarPreview
                        )
                    }
                }
            }
        }

        // Data Persistence & JSON Backup / Restore Card
        item {
            WonderlandCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Data Persistence & Backup", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "Your buildings, contacts, tasks, and shifts persist automatically across in-place app updates. Export a JSON backup anytime to Google Drive or device storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = onExportBackup) {
                            Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                        OutlinedButton(onClick = onImportBackup) {
                            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore JSON")
                        }
                    }
                    if (backupStatusMessage != null) {
                        Text(
                            text = backupStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // In-App Updater Card
        item {
            WonderlandCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("In-App Updates", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Installed: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = updateManifestUrl,
                        onValueChange = onUpdateManifestUrlChange,
                        label = { Text("Hosted version.json URL (e.g. GitHub Releases)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onCheckForUpdates,
                            enabled = updateState !is UpdateCheckResult.Checking && updateState !is UpdateCheckResult.Downloading
                        ) {
                            Text("Check for Updates")
                        }
                    }

                    when (updateState) {
                        UpdateCheckResult.Idle -> Unit
                        UpdateCheckResult.Checking -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Checking Looking-Glass release channel...", style = MaterialTheme.typography.bodySmall)
                        }
                        is UpdateCheckResult.UpToDate -> {
                            Text(
                                "You're up to date on v${updateState.currentVersionName}!",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalWonderlandColors.current.successAccent
                            )
                        }
                        is UpdateCheckResult.UpdateAvailable -> {
                            Text(
                                "New version available: v${updateState.remote.versionName} (build ${updateState.remote.versionCode})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (updateState.remote.releaseNotes.isNotBlank()) {
                                Text(
                                    updateState.remote.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(onClick = { onDownloadAndInstallUpdate(updateState.remote) }) {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download & Update In-Place")
                            }
                        }
                        is UpdateCheckResult.Downloading -> {
                            LinearProgressIndicator(
                                progress = { updateState.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Downloading update (${updateState.progressPercent}%)...", style = MaterialTheme.typography.bodySmall)
                        }
                        is UpdateCheckResult.Error -> {
                            Text(
                                updateState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // Sample Data Card
        item {
            WonderlandCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Wonderland Sample Data", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Explore with pre-built buildings, employees, customers, shifts, and tasks—or clear everything for a clean slate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = onLoadSampleData) {
                            Text("Load Sample Data")
                        }
                        OutlinedButton(onClick = onClearAllData) {
                            Text("Clear All Data")
                        }
                    }
                }
            }
        }
    }
}
