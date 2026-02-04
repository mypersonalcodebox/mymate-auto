package com.mymate.auto.ui.memories

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymate.auto.data.model.Memory
import com.mymate.auto.data.model.MemoryCategory
import com.mymate.auto.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    onNavigateBack: () -> Unit,
    viewModel: MemoriesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val memories by viewModel.filteredMemories.collectAsState(initial = emptyList())
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Memory?>(null) }
    var editingMemory by remember { mutableStateOf<Memory?>(null) }
    
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show snackbar for messages
    LaunchedEffect(uiState.message, uiState.error) {
        val message = uiState.message ?: uiState.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    
    // Add/Edit dialog
    if (showAddDialog || editingMemory != null) {
        AddMemoryDialog(
            initialContent = editingMemory?.content ?: "",
            initialCategory = editingMemory?.category ?: MemoryCategory.GENERAL,
            isEdit = editingMemory != null,
            onDismiss = {
                showAddDialog = false
                editingMemory = null
            },
            onConfirm = { content, category ->
                if (editingMemory != null) {
                    viewModel.updateMemory(editingMemory!!.copy(content = content, category = category))
                } else {
                    viewModel.addMemory(content, category)
                }
                showAddDialog = false
                editingMemory = null
            }
        )
    }
    
    // Delete confirmation dialog
    showDeleteDialog?.let { memory ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Verwijderen?") },
            text = { 
                Text(
                    memory.content.take(100) + if (memory.content.length > 100) "..." else ""
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMemory(memory)
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
                title = { Text("📝 Memories", fontWeight = FontWeight.Bold) },
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
                Icon(Icons.Default.Add, contentDescription = "Toevoegen", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Zoeken...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Wissen")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(24.dp)
            )
            
            // Category filters
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        onClick = { viewModel.setCategory(null) },
                        label = { Text("Alles") },
                        selected = selectedCategory == null,
                        leadingIcon = if (selectedCategory == null) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
                
                items(MemoryCategory.entries.toTypedArray()) { category ->
                    FilterChip(
                        onClick = { viewModel.setCategory(category) },
                        label = { Text(category.toDisplayName()) },
                        selected = selectedCategory == category,
                        leadingIcon = {
                            Text(category.toEmoji(), modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
            
            // Memory list
            if (memories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isNotEmpty()) "Geen resultaten" else "Geen memories",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Sla dingen op die je wilt onthouden",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(memories, key = { it.id }) { memory ->
                        MemoryCard(
                            memory = memory,
                            onEdit = { editingMemory = memory },
                            onDelete = { showDeleteDialog = memory }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryCard(
    memory: Memory,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(memory.category.toEmoji())
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        memory.category.toDisplayName(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Bewerken",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Verwijderen",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                memory.content,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                formatDate(memory.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemoryDialog(
    initialContent: String,
    initialCategory: MemoryCategory,
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (content: String, category: MemoryCategory) -> Unit
) {
    var content by remember { mutableStateOf(initialContent) }
    var category by remember { mutableStateOf(initialCategory) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Bewerken" else "Nieuw memory") },
        text = {
            Column {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Wat wil je onthouden?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                ExposedDropdownMenuBox(
                    expanded = showCategoryDropdown,
                    onExpandedChange = { showCategoryDropdown = it }
                ) {
                    OutlinedTextField(
                        value = "${category.toEmoji()} ${category.toDisplayName()}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categorie") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { showCategoryDropdown = false }
                    ) {
                        MemoryCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("${cat.toEmoji()} ${cat.toDisplayName()}") },
                                onClick = {
                                    category = cat
                                    showCategoryDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(content, category) },
                enabled = content.isNotBlank()
            ) {
                Text(if (isEdit) "Opslaan" else "Toevoegen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuleren")
            }
        }
    )
}

private fun MemoryCategory.toEmoji(): String = when (this) {
    MemoryCategory.GENERAL -> "📝"
    MemoryCategory.PERSONAL -> "👤"
    MemoryCategory.WORK -> "💼"
    MemoryCategory.LOCATION -> "📍"
    MemoryCategory.CONTACT -> "👥"
    MemoryCategory.TODO -> "✅"
}

private fun MemoryCategory.toDisplayName(): String = when (this) {
    MemoryCategory.GENERAL -> "Algemeen"
    MemoryCategory.PERSONAL -> "Persoonlijk"
    MemoryCategory.WORK -> "Werk"
    MemoryCategory.LOCATION -> "Locatie"
    MemoryCategory.CONTACT -> "Contact"
    MemoryCategory.TODO -> "To-do"
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("nl", "NL"))
    return sdf.format(Date(timestamp))
}
