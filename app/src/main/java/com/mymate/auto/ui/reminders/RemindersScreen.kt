package com.mymate.auto.ui.reminders

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymate.auto.data.model.Reminder
import com.mymate.auto.data.model.RepeatType
import com.mymate.auto.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onNavigateBack: () -> Unit,
    viewModel: RemindersViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeReminders by viewModel.activeReminders.collectAsState(initial = emptyList())
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Reminder?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show snackbar for messages
    LaunchedEffect(uiState.message, uiState.error) {
        val message = uiState.message ?: uiState.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    
    // Add reminder dialog
    if (showAddDialog) {
        AddReminderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, description, triggerTime, repeatType ->
                viewModel.addReminder(title, description, triggerTime, repeatType)
                showAddDialog = false
            }
        )
    }
    
    // Delete confirmation dialog
    showDeleteDialog?.let { reminder ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Verwijderen?") },
            text = { Text(reminder.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteReminder(reminder)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Verwijderen", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Annuleren")
                }
            }
        )
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("🔔 Herinneringen", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = PrimaryBlue
            ) {
                Icon(Icons.Default.Add, contentDescription = "Toevoegen", tint = Color.White)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Quick add buttons
            Text(
                "Snel toevoegen",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    QuickReminderChip(
                        label = "5 min",
                        onClick = { 
                            // Would need to show input dialog for title
                            viewModel.addQuickReminder("Herinnering", 5) 
                        }
                    )
                }
                item {
                    QuickReminderChip(
                        label = "15 min",
                        onClick = { viewModel.addQuickReminder("Herinnering", 15) }
                    )
                }
                item {
                    QuickReminderChip(
                        label = "30 min",
                        onClick = { viewModel.addQuickReminder("Herinnering", 30) }
                    )
                }
                item {
                    QuickReminderChip(
                        label = "1 uur",
                        onClick = { viewModel.addQuickReminder("Herinnering", 60) }
                    )
                }
                item {
                    QuickReminderChip(
                        label = "Morgen",
                        onClick = { 
                            val tomorrow = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, 1)
                                set(Calendar.HOUR_OF_DAY, 9)
                                set(Calendar.MINUTE, 0)
                            }
                            viewModel.addReminder("Herinnering", null, tomorrow.timeInMillis)
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Reminders list
            if (activeReminders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Geen herinneringen",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Zeg 'herinner me over X minuten' of\nvoeg er een toe met de knop",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeReminders, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            onComplete = { viewModel.completeReminder(reminder) },
                            onDelete = { showDeleteDialog = reminder }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickReminderChip(
    label: String,
    onClick: () -> Unit
) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(label) },
        icon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) }
    )
}

@Composable
fun ReminderCard(
    reminder: Reminder,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOverdue = reminder.triggerTime < System.currentTimeMillis()
    val cardColor = if (isOverdue) {
        ErrorRed.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Complete button
            IconButton(
                onClick = onComplete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Afronden",
                    tint = SuccessGreen,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                reminder.description?.let { desc ->
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isOverdue) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        formatReminderTime(reminder.triggerTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (reminder.repeatType != RepeatType.NONE) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            reminder.repeatType.toDisplayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Verwijderen",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String?, triggerTime: Long, repeatType: RepeatType) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var repeatType by remember { mutableStateOf(RepeatType.NONE) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    // Date picker
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newCal = Calendar.getInstance().apply { timeInMillis = millis }
                        selectedDate.set(Calendar.YEAR, newCal.get(Calendar.YEAR))
                        selectedDate.set(Calendar.MONTH, newCal.get(Calendar.MONTH))
                        selectedDate.set(Calendar.DAY_OF_MONTH, newCal.get(Calendar.DAY_OF_MONTH))
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Annuleren")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    // Time picker
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedDate.get(Calendar.HOUR_OF_DAY),
            initialMinute = selectedDate.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Kies tijd") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedDate.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    selectedDate.set(Calendar.MINUTE, timePickerState.minute)
                    showTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Annuleren")
                }
            }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nieuwe herinnering") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Beschrijving (optioneel)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Date/time buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(formatDate(selectedDate.timeInMillis))
                    }
                    
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(formatTime(selectedDate.timeInMillis))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Repeat type
                Text("Herhalen", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RepeatType.entries.toTypedArray()) { type ->
                        FilterChip(
                            onClick = { repeatType = type },
                            label = { Text(type.toDisplayName()) },
                            selected = repeatType == type
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        title,
                        description.ifBlank { null },
                        selectedDate.timeInMillis,
                        repeatType
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Text("Toevoegen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuleren")
            }
        }
    )
}

private fun RepeatType.toDisplayName(): String = when (this) {
    RepeatType.NONE -> "Eenmalig"
    RepeatType.DAILY -> "Dagelijks"
    RepeatType.WEEKLY -> "Wekelijks"
    RepeatType.MONTHLY -> "Maandelijks"
}

private fun formatReminderTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = timestamp - now
    
    return when {
        diff < 0 -> "Verlopen"
        diff < 60 * 1000 -> "Nu"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} min"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} uur"
        else -> {
            val sdf = SimpleDateFormat("d MMM, HH:mm", Locale("nl", "NL"))
            sdf.format(Date(timestamp))
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("d MMM", Locale("nl", "NL"))
    return sdf.format(Date(timestamp))
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
