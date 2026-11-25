package com.wonderland.taschasplanner.ui.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wonderland.taschasplanner.data.local.ContactEntity
import com.wonderland.taschasplanner.data.local.ContactType
import com.wonderland.taschasplanner.data.model.WonderlandTimeFormat
import com.wonderland.taschasplanner.ui.WonderlandUiState
import com.wonderland.taschasplanner.ui.components.CardSuitBadge
import com.wonderland.taschasplanner.ui.components.CheshireEmptyState
import com.wonderland.taschasplanner.ui.components.OrnateGoldDivider
import com.wonderland.taschasplanner.ui.components.WonderlandCard
import com.wonderland.taschasplanner.ui.theme.LocalWonderlandColors
import java.time.LocalDate

private enum class ContactFilter(val label: String) {
    ALL("All"),
    EMPLOYEES("Employees"),
    CUSTOMERS("Customers")
}

@Composable
fun ContactsScreen(
    state: WonderlandUiState,
    isExpandedWidth: Boolean,
    onAddContact: (ContactType) -> Unit,
    onEditContact: (ContactEntity) -> Unit,
    onToggleFavorite: (ContactEntity) -> Unit
) {
    var filter by remember { mutableStateOf(ContactFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var selectedContactId by remember { mutableStateOf<Long?>(null) }

    val filteredContacts = remember(state.contacts, filter, query) {
        state.contacts.filter { c ->
            val matchesType = when (filter) {
                ContactFilter.ALL -> true
                ContactFilter.EMPLOYEES -> c.type == ContactType.EMPLOYEE
                ContactFilter.CUSTOMERS -> c.type == ContactType.CUSTOMER
            }
            val q = query.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                c.fullName.lowercase().contains(q) ||
                c.roleOrCompany.lowercase().contains(q) ||
                c.phone.lowercase().contains(q) ||
                c.email.lowercase().contains(q)
            matchesType && matchesQuery
        }
    }

    val selectedContact = selectedContactId?.let { id -> state.contacts.find { it.id == id } }
        ?: if (isExpandedWidth) filteredContacts.firstOrNull() else null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val defaultType = if (filter == ContactFilter.CUSTOMERS) ContactType.CUSTOMER else ContactType.EMPLOYEE
                    onAddContact(defaultType)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add contact")
            }
        }
    ) { innerPadding ->
        val listPane: @Composable (Modifier) -> Unit = { mod ->
            Column(
                modifier = mod.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Wonderland Directory", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${state.employees.size} employees • ${state.customers.size} customers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search name, role, company, phone...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ContactFilter.entries.forEachIndexed { idx, f ->
                        SegmentedButton(
                            selected = filter == f,
                            onClick = { filter = f },
                            shape = SegmentedButtonDefaults.itemShape(idx, ContactFilter.entries.size)
                        ) {
                            Text(f.label)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (filteredContacts.isEmpty()) {
                    CheshireEmptyState(
                        title = "No Contacts Found",
                        message = "Tap + to add an employee or customer to Tascha's directory."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredContacts, key = { it.id }) { contact ->
                            val bldg = contact.buildingId?.let { state.buildingsById[it] }
                            val accent = if (contact.type == ContactType.EMPLOYEE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }

                            WonderlandCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = if (selectedContact?.id == contact.id) accent else null,
                                onClick = { selectedContactId = contact.id }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CardSuitBadge(suit = contact.cardSuit, accentColor = accent)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.fullName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val sub = listOfNotNull(
                                            contact.roleOrCompany.takeIf { it.isNotBlank() },
                                            bldg?.name
                                        ).joinToString(" • ")
                                        if (sub.isNotBlank()) {
                                            Text(
                                                text = sub,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onToggleFavorite(contact) }) {
                                        Icon(
                                            imageVector = if (contact.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                            contentDescription = "Favorite",
                                            tint = if (contact.isFavorite) LocalWonderlandColors.current.goldTrim
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
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
                listPane(Modifier.weight(1f).fillMaxHeight())
                if (selectedContact != null) {
                    ContactDetailPane(
                        contact = selectedContact,
                        state = state,
                        showBack = false,
                        onBack = {},
                        onEdit = { onEditContact(selectedContact) },
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .padding(end = 16.dp, top = 10.dp, bottom = 10.dp)
                    )
                }
            }
        } else {
            if (selectedContact != null) {
                ContactDetailPane(
                    contact = selectedContact,
                    state = state,
                    showBack = true,
                    onBack = { selectedContactId = null },
                    onEdit = { onEditContact(selectedContact) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp)
                )
            } else {
                listPane(Modifier.fillMaxSize().padding(innerPadding))
            }
        }
    }
}

@Composable
private fun ContactDetailPane(
    contact: ContactEntity,
    state: WonderlandUiState,
    showBack: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = if (contact.type == ContactType.EMPLOYEE) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }
    val building = contact.buildingId?.let { state.buildingsById[it] }
    val relatedTasks = state.tasks.filter {
        it.assignedEmployeeId == contact.id || it.customerId == contact.id
    }
    val relatedShifts = state.shifts.filter { it.employeeId == contact.id }

    WonderlandCard(modifier = modifier, accentColor = accent) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to list")
                    }
                } else {
                    Text(
                        text = if (contact.type == ContactType.EMPLOYEE) "Employee Profile" else "Customer Profile",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit contact")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                CardSuitBadge(suit = contact.cardSuit, accentColor = accent, size = 56.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(contact.fullName, style = MaterialTheme.typography.headlineSmall)
                    if (contact.roleOrCompany.isNotBlank()) {
                        Text(
                            contact.roleOrCompany,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (building != null) {
                        Text(
                            "Primary Location: ${building.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(building.colorHex)
                        )
                    }
                }
            }

            // Quick Contact Actions: Call, SMS, Email
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (contact.phone.isNotBlank()) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}")))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Call")
                    }
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.phone}")))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Text")
                    }
                }
                if (contact.email.isNotBlank()) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${contact.email}")))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Email")
                    }
                }
            }

            if (contact.phone.isNotBlank()) {
                Text("Phone: ${contact.phone}", style = MaterialTheme.typography.bodyMedium)
            }
            if (contact.email.isNotBlank()) {
                Text("Email: ${contact.email}", style = MaterialTheme.typography.bodyMedium)
            }
            if (contact.birthdayMonth != null && contact.birthdayDay != null) {
                val syncedSuffix = if (contact.linkedBirthdayEventId != null) " • Synced to Google Cal" else ""
                Text(
                    text = "\uD83C\uDF82 Birthday: ${WonderlandTimeFormat.formatBirthday(contact.birthdayMonth, contact.birthdayDay)}$syncedSuffix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (contact.notes.isNotBlank()) {
                Text("Notes: ${contact.notes}", style = MaterialTheme.typography.bodyMedium)
            }

            OrnateGoldDivider()

            Text("Linked Tasks (${relatedTasks.size})", style = MaterialTheme.typography.titleMedium)
            if (relatedTasks.isEmpty()) {
                Text(
                    "No tasks linked yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                relatedTasks.forEach { t ->
                    Text(
                        text = "• ${t.title} (${WonderlandTimeFormat.formatShortDate(LocalDate.ofEpochDay(t.dueDateEpochDay))} • ${t.status.displayLabel})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (contact.type == ContactType.EMPLOYEE) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Scheduled Shifts (${relatedShifts.size})", style = MaterialTheme.typography.titleMedium)
                if (relatedShifts.isEmpty()) {
                    Text(
                        "No shifts scheduled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    relatedShifts.forEach { s ->
                        val bName = state.buildingsById[s.buildingId]?.name ?: "Building"
                        Text(
                            text = "• ${WonderlandTimeFormat.formatShortDate(LocalDate.ofEpochDay(s.dateEpochDay))} ${WonderlandTimeFormat.formatShiftSchedule(s)} @ $bName (${s.status.displayLabel})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
