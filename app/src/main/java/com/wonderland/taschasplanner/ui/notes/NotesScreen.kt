package com.wonderland.taschasplanner.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wonderland.taschasplanner.data.local.NoteTodoEntity
import com.wonderland.taschasplanner.data.model.WonderlandTimeFormat
import com.wonderland.taschasplanner.ui.components.CheshireEmptyState
import com.wonderland.taschasplanner.ui.components.OrnateGoldDivider
import com.wonderland.taschasplanner.ui.components.WonderlandCard
import com.wonderland.taschasplanner.ui.theme.BuildingAccentColors
import com.wonderland.taschasplanner.ui.theme.LocalWonderlandColors
import kotlinx.coroutines.delay

private enum class NoteFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    COMPLETED("Completed")
}

@Composable
fun NotesScreen(
    notes: List<NoteTodoEntity>,
    isExpandedWidth: Boolean,
    onCreateNote: ((Long) -> Unit) -> Unit,
    onSaveNote: (NoteTodoEntity) -> Unit,
    onToggleCompleted: (NoteTodoEntity) -> Unit,
    onTogglePinned: (NoteTodoEntity) -> Unit,
    onDeleteNote: (NoteTodoEntity) -> Unit,
    onAssignToCalendar: (NoteTodoEntity) -> Unit
) {
    var filter by remember { mutableStateOf(NoteFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var selectedNoteId by remember { mutableStateOf<Long?>(null) }

    val activeCount = remember(notes) { notes.count { !it.isCompleted } }
    val completedCount = remember(notes) { notes.count { it.isCompleted } }

    val filteredNotes = remember(notes, filter, query) {
        val q = query.trim().lowercase()
        notes.filter { note ->
            val matchesFilter = when (filter) {
                NoteFilter.ALL -> true
                NoteFilter.ACTIVE -> !note.isCompleted
                NoteFilter.COMPLETED -> note.isCompleted
            }
            val matchesQuery = q.isEmpty() ||
                note.header.lowercase().contains(q) ||
                note.body.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    val selectedNote = selectedNoteId?.let { id -> notes.find { it.id == id } }
        ?: if (isExpandedWidth) filteredNotes.firstOrNull() else null

    val createAndSelect: () -> Unit = {
        onCreateNote { createdId ->
            if (filter == NoteFilter.COMPLETED) filter = NoteFilter.ALL
            selectedNoteId = createdId
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!isExpandedWidth && selectedNote == null) {
                FloatingActionButton(
                    onClick = createAndSelect,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "New note")
                }
            }
        }
    ) { innerPadding ->
        val listPane: @Composable (Modifier) -> Unit = { mod ->
            Column(
                modifier = mod.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Outlined.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Wonderland Notes", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "$activeCount active • $completedCount completed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalButton(onClick = createAndSelect) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search headers or notes...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    NoteFilter.entries.forEachIndexed { idx, f ->
                        SegmentedButton(
                            selected = filter == f,
                            onClick = { filter = f },
                            shape = SegmentedButtonDefaults.itemShape(idx, NoteFilter.entries.size)
                        ) {
                            Text(f.label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredNotes.isEmpty()) {
                    CheshireEmptyState(
                        title = if (notes.isEmpty()) "Blank Notebook" else "No Matching Notes",
                        message = if (notes.isEmpty()) {
                            "Tap + New to jot down upcoming plans, ideas, or quick to-dos—then check them off or assign them to the calendar anytime."
                        } else {
                            "Try another filter or search term."
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            NoteIndexCard(
                                note = note,
                                isSelected = selectedNote?.id == note.id,
                                onClick = { selectedNoteId = note.id },
                                onToggleCompleted = { onToggleCompleted(note) }
                            )
                        }
                    }
                }
            }
        }

        if (isExpandedWidth) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                listPane(Modifier.weight(0.39f).fillMaxHeight())
                Box(
                    modifier = Modifier
                        .weight(0.61f)
                        .fillMaxHeight()
                        .padding(end = 16.dp, top = 10.dp, bottom = 10.dp)
                ) {
                    if (selectedNote != null) {
                        OneNotePageCanvas(
                            note = selectedNote,
                            showBack = false,
                            onBack = {},
                            onSaveNote = onSaveNote,
                            onToggleCompleted = onToggleCompleted,
                            onTogglePinned = onTogglePinned,
                            onDeleteNote = {
                                onDeleteNote(it)
                                if (selectedNoteId == it.id) selectedNoteId = null
                            },
                            onAssignToCalendar = onAssignToCalendar,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        WonderlandCard(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CheshireEmptyState(
                                        title = "Select or Start a Page",
                                        message = "Pick a note from your notebook index or tap + New to open a fresh page."
                                    )
                                    FilledTonalButton(onClick = createAndSelect) {
                                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create First Note")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (selectedNote != null) {
                OneNotePageCanvas(
                    note = selectedNote,
                    showBack = true,
                    onBack = { selectedNoteId = null },
                    onSaveNote = onSaveNote,
                    onToggleCompleted = onToggleCompleted,
                    onTogglePinned = onTogglePinned,
                    onDeleteNote = {
                        onDeleteNote(it)
                        selectedNoteId = null
                    },
                    onAssignToCalendar = onAssignToCalendar,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(12.dp)
                )
            } else {
                listPane(Modifier.fillMaxSize().padding(innerPadding))
            }
        }
    }
}

@Composable
private fun NoteIndexCard(
    note: NoteTodoEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleCompleted: () -> Unit
) {
    val accent = Color(note.accentColorHex)
    val strike = if (note.isCompleted) TextDecoration.LineThrough else TextDecoration.None

    WonderlandCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (note.isCompleted) 0.72f else 1f),
        accentColor = if (isSelected) accent else null,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (note.isCompleted) MaterialTheme.colorScheme.outline else accent)
            )
            IconButton(
                onClick = onToggleCompleted,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (note.isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (note.isCompleted) "Mark active" else "Mark completed",
                    tint = if (note.isCompleted) LocalWonderlandColors.current.successAccent else accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = note.header.ifBlank { "Untitled Note" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        textDecoration = strike,
                        color = if (note.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (note.isPinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = LocalWonderlandColors.current.goldTrim,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = WonderlandTimeFormat.formatTimestamp(note.createdAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (note.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = note.body,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = strike,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (note.linkedTaskId != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Scheduled in Calendar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OneNotePageCanvas(
    note: NoteTodoEntity,
    showBack: Boolean,
    onBack: () -> Unit,
    onSaveNote: (NoteTodoEntity) -> Unit,
    onToggleCompleted: (NoteTodoEntity) -> Unit,
    onTogglePinned: (NoteTodoEntity) -> Unit,
    onDeleteNote: (NoteTodoEntity) -> Unit,
    onAssignToCalendar: (NoteTodoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var headerDraft by remember(note.id) { mutableStateOf(note.header) }
    var bodyDraft by remember(note.id) { mutableStateOf(note.body) }
    var accentDraft by remember(note.id) { mutableLongStateOf(note.accentColorHex) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val latestNote by rememberUpdatedState(note)
    val latestHeader by rememberUpdatedState(headerDraft)
    val latestBody by rememberUpdatedState(bodyDraft)
    val latestAccent by rememberUpdatedState(accentDraft)
    val latestOnSave by rememberUpdatedState(onSaveNote)

    fun buildLatestDraftEntity(): NoteTodoEntity = latestNote.copy(
        header = latestHeader,
        body = latestBody,
        accentColorHex = latestAccent
    )

    fun hasUnsavedChanges(): Boolean =
        latestHeader != latestNote.header ||
            latestBody != latestNote.body ||
            latestAccent != latestNote.accentColorHex

    // Auto-save smoothly as Tascha types (OneNote feel)
    LaunchedEffect(note.id, headerDraft, bodyDraft, accentDraft) {
        if (headerDraft != note.header || bodyDraft != note.body || accentDraft != note.accentColorHex) {
            delay(550L)
            latestOnSave(buildLatestDraftEntity())
        }
    }

    // Flush any pending draft changes immediately when switching pages or leaving the screen
    DisposableEffect(note.id) {
        onDispose {
            if (hasUnsavedChanges()) {
                latestOnSave(buildLatestDraftEntity())
            }
        }
    }

    val accent = Color(accentDraft)
    val strike = if (note.isCompleted) TextDecoration.LineThrough else TextDecoration.None
    val textColor = if (note.isCompleted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    WonderlandCard(
        modifier = modifier,
        accentColor = accent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top OneNote Page Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (showBack) {
                        IconButton(
                            onClick = {
                                if (hasUnsavedChanges()) latestOnSave(buildLatestDraftEntity())
                                onBack()
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to notes")
                        }
                    }
                    AssistChip(
                        onClick = {
                            val current = buildLatestDraftEntity()
                            if (hasUnsavedChanges()) latestOnSave(current)
                            onToggleCompleted(current)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (note.isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (note.isCompleted) LocalWonderlandColors.current.successAccent else accent,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text(if (note.isCompleted) "Completed" else "Complete") }
                    )
                    FilledTonalButton(
                        onClick = {
                            val current = buildLatestDraftEntity()
                            if (hasUnsavedChanges()) latestOnSave(current)
                            onAssignToCalendar(current)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.EventAvailable,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Assign to Cal")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val current = buildLatestDraftEntity()
                            if (hasUnsavedChanges()) latestOnSave(current)
                            onTogglePinned(current)
                        }
                    ) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (note.isPinned) "Unpin note" else "Pin note",
                            tint = if (note.isPinned) LocalWonderlandColors.current.goldTrim else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete note",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Color tag dots + Status badges row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuildingAccentColors.forEach { hex ->
                        val isChosen = accentDraft == hex
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (isChosen) 2.5.dp else 1.dp,
                                    color = if (isChosen) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { accentDraft = hex }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (note.linkedTaskId != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Scheduled",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (note.isCompleted && note.completedAtMillis != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = LocalWonderlandColors.current.successAccent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "Done ${WonderlandTimeFormat.formatTimestamp(note.completedAtMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalWonderlandColors.current.successAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Scrollable OneNote Page Body: Timestamp -> Header -> Gold Rule -> Open Freeform Text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // OneNote-style prominent timestamp above the header
                Text(
                    text = WonderlandTimeFormat.formatNoteTimestamp(note.createdAtMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    letterSpacing = 0.3.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Editable Bold Header (strikes through when completed)
                BasicTextField(
                    value = headerDraft,
                    onValueChange = { headerDraft = it },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        textDecoration = strike
                    ),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (headerDraft.isEmpty()) {
                                Text(
                                    text = "Header / Upcoming Plan...",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                OrnateGoldDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Spacious Open Freeform Text Area (strikes through when completed)
                BasicTextField(
                    value = bodyDraft,
                    onValueChange = { bodyDraft = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = textColor,
                        lineHeight = 26.sp,
                        textDecoration = strike
                    ),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (bodyDraft.isEmpty()) {
                                Text(
                                    text = "Jot down plans, reminders, checklists, or building notes here...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Note?") },
            text = {
                Text("Permanently remove \"${headerDraft.ifBlank { "Untitled Note" }}\" from your Wonderland Notes?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteNote(note)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
