package com.mymate.auto.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mymate.auto.data.model.ChatMessage
import com.mymate.auto.data.model.QuickAction
import com.mymate.auto.data.remote.WebSocketManager
import com.mymate.auto.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val connectionState by viewModel.connectionState.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Voice input state
    var pendingAction by remember { mutableStateOf<QuickAction?>(null) }
    var showInputDialog by remember { mutableStateOf(false) }
    var dialogInputText by remember { mutableStateOf("") }
    
    // Voice recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spokenText.isNullOrBlank()) {
            if (pendingAction != null) {
                // Complete the pending action with voice input
                val action = pendingAction!!
                val fullQuery = if (action.query.isNotEmpty()) {
                    "${action.query} $spokenText"
                } else {
                    spokenText
                }
                viewModel.sendMessage(fullQuery)
                pendingAction = null
            } else {
                // Direct voice input
                viewModel.sendMessage(spokenText)
            }
        }
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecognition(context, speechLauncher)
        } else {
            Toast.makeText(context, "Microfoon permissie nodig voor spraak", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun launchVoiceInput() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition(context, speechLauncher)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("MyMate", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        ConnectionIndicator(connectionState)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Wis geschiedenis")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Instellingen")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Quick Actions Row
            QuickActionsRow(
                actions = uiState.quickActions,
                onActionClick = { action ->
                    if (action.query.isEmpty()) {
                        // Action needs user input - show dialog
                        pendingAction = action
                        dialogInputText = ""
                        showInputDialog = true
                    } else {
                        viewModel.executeQuickAction(action)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Input dialog for actions that need user input
            if (showInputDialog && pendingAction != null) {
                AlertDialog(
                    onDismissRequest = { 
                        showInputDialog = false
                        pendingAction = null
                    },
                    title = { 
                        Text("${pendingAction!!.emoji} ${pendingAction!!.title}")
                    },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = dialogInputText,
                                onValueChange = { dialogInputText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Typ of gebruik spraak...") },
                                singleLine = false,
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    pendingAction?.let { action ->
                                        launchVoiceInput()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gebruik spraak")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (dialogInputText.isNotBlank()) {
                                    val action = pendingAction!!
                                    val fullQuery = if (action.query.isNotEmpty()) {
                                        "${action.query} $dialogInputText"
                                    } else {
                                        dialogInputText
                                    }
                                    viewModel.sendMessage(fullQuery)
                                }
                                showInputDialog = false
                                pendingAction = null
                            },
                            enabled = dialogInputText.isNotBlank()
                        ) {
                            Text("Verstuur")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            showInputDialog = false
                            pendingAction = null
                        }) {
                            Text("Annuleren")
                        }
                    }
                )
            }
            
            // Chat Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            
            // Error display
            AnimatedVisibility(visible = uiState.error != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = ErrorRed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            uiState.error ?: "",
                            color = ErrorRed,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, contentDescription = "Sluiten")
                        }
                    }
                }
            }
            
            // Loading indicator
            AnimatedVisibility(visible = uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = PrimaryBlue
                )
            }
            
            // Input Row
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Typ een bericht...") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                    focusManager.clearFocus()
                                }
                            }
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Voice input button
                    IconButton(
                        onClick = { launchVoiceInput() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Spraak invoer",
                            tint = PrimaryBlue
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    FloatingActionButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                                focusManager.clearFocus()
                            }
                        },
                        containerColor = if (inputText.isNotBlank()) PrimaryBlue else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Verstuur"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionIndicator(state: WebSocketManager.ConnectionState) {
    // Always show HTTP mode since WebSocket is disabled by default
    // The app uses HTTP for all communication with the webhook
    val (color, text) = SuccessGreen to "HTTP"
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = color
        )
    }
}

@Composable
fun QuickActionsRow(
    actions: List<QuickAction>,
    onActionClick: (QuickAction) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(actions.take(15)) { action ->
            QuickActionChip(
                action = action,
                onClick = { onActionClick(action) }
            )
        }
    }
}

@Composable
fun QuickActionChip(
    action: QuickAction,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                "${action.emoji} ${action.title}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            width = if (action.usageCount > 0) 2.dp else 1.dp,
            color = if (action.usageCount > 0) PrimaryBlue else MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.isFromUser
    val bubbleColor = if (isUser) {
        UserMessageBubble
    } else {
        if (MaterialTheme.colorScheme.background == BackgroundDark) {
            BotMessageBubble
        } else {
            BotMessageBubbleLight
        }
    }
    val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
    
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    color = textColor,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(message.timestamp),
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun startVoiceRecognition(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        Toast.makeText(context, "Spraakherkenning niet beschikbaar", Toast.LENGTH_SHORT).show()
        return
    }
    
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "nl-NL")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Spreek je bericht in...")
    }
    
    try {
        launcher.launch(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Kon spraakherkenning niet starten", Toast.LENGTH_SHORT).show()
    }
}
