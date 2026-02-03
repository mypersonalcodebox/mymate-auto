package com.mymate.auto.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymate.auto.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showWebhookDialog by remember { mutableStateOf(false) }
    var tempWebhookUrl by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instellingen") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Connection Section
            SettingsSection(title = "Verbinding") {
                SettingsItem(
                    icon = Icons.Default.Link,
                    title = "Webhook URL",
                    subtitle = uiState.webhookUrl,
                    onClick = {
                        tempWebhookUrl = uiState.webhookUrl
                        showWebhookDialog = true
                    }
                )
                
                SettingsSwitch(
                    icon = Icons.Default.Wifi,
                    title = "WebSocket verbinding",
                    subtitle = "Real-time berichten ontvangen",
                    checked = uiState.webSocketEnabled,
                    onCheckedChange = { viewModel.setWebSocketEnabled(it) }
                )
                
                SettingsSwitch(
                    icon = Icons.Default.Refresh,
                    title = "Auto-reconnect",
                    subtitle = "Automatisch herverbinden bij disconnect",
                    checked = uiState.autoReconnect,
                    onCheckedChange = { viewModel.setAutoReconnect(it) }
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Features Section
            SettingsSection(title = "Functies") {
                SettingsSwitch(
                    icon = Icons.Default.VolumeUp,
                    title = "Text-to-Speech",
                    subtitle = "Antwoorden voorlezen in Auto",
                    checked = uiState.ttsEnabled,
                    onCheckedChange = { viewModel.setTtsEnabled(it) }
                )
                
                SettingsSwitch(
                    icon = Icons.Default.Notifications,
                    title = "Notificaties",
                    subtitle = "Push notificaties voor nieuwe berichten",
                    checked = uiState.notificationsEnabled,
                    onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Appearance Section
            SettingsSection(title = "Weergave") {
                SettingsSwitch(
                    icon = Icons.Default.DarkMode,
                    title = "Donkere modus",
                    subtitle = "Gebruik donker thema",
                    checked = uiState.darkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) }
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Data Section
            SettingsSection(title = "Data") {
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "Wis chat geschiedenis",
                    subtitle = "Verwijder alle berichten",
                    onClick = { viewModel.clearChatHistory() }
                )
                
                SettingsItem(
                    icon = Icons.Default.RestartAlt,
                    title = "Reset voorkeuren",
                    subtitle = "Herstel standaardinstellingen",
                    onClick = { viewModel.resetPreferences() }
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // About Section
            SettingsSection(title = "Over") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Versie",
                    subtitle = "2.0",
                    onClick = { }
                )
                
                SettingsItem(
                    icon = Icons.Default.DirectionsCar,
                    title = "Android Auto",
                    subtitle = "Verbind met je auto om te gebruiken",
                    onClick = { }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    // Webhook URL Dialog
    if (showWebhookDialog) {
        AlertDialog(
            onDismissRequest = { showWebhookDialog = false },
            title = { Text("Webhook URL") },
            text = {
                OutlinedTextField(
                    value = tempWebhookUrl,
                    onValueChange = { tempWebhookUrl = it },
                    label = { Text("URL") },
                    placeholder = { Text("http://100.124.24.27:18791/auto") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setWebhookUrl(tempWebhookUrl)
                        showWebhookDialog = false
                    }
                ) {
                    Text("Opslaan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebhookDialog = false }) {
                    Text("Annuleren")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = PrimaryBlue,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PrimaryBlue,
                    checkedTrackColor = PrimaryBlue.copy(alpha = 0.5f)
                )
            )
        }
    }
}
