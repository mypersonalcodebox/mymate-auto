package com.mymate.auto.ui.conversation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymate.auto.data.model.ConversationMessage
import com.mymate.auto.data.remote.OpenClawWebSocket
import com.mymate.auto.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConversationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val connectionState by viewModel.connectionState.collectAsState()
    val topic by viewModel.conversationTopic.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    var showNewConversationDialog by remember { mutableStateOf(false) }
    
    // Voice recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spokenText.isNullOrBlank()) {
            viewModel.sendMessage(spokenText)
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
    
    // New conversation confirmation dialog
    if (showNewConversationDialog) {
        AlertDialog(
            onDismissRequest = { showNewConversationDialog = false },
            title = { Text("Nieuw gesprek starten?") },
            text = { 
                Text("Dit wist de huidige gespreksgeschiedenis. De AI verliest de context van dit gesprek.") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.startNewConversation()
                        showNewConversationDialog = false
                    }
                ) {
                    Text("Nieuw gesprek", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewConversationDialog = false }) {
                    Text("Annuleren")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("💬 Gesprek", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            ConversationConnectionIndicator(connectionState)
                        }
                        topic?.let { t ->
                            Text(
                                text = t,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    // Message count badge
                    if (uiState.messageCount > 0) {
                        Badge(
                            containerColor = PrimaryBlue,
                            contentColor = Color.White
                        ) {
                            Text("${uiState.messageCount}")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    // New conversation button
                    IconButton(
                        onClick = { showNewConversationDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Nieuw gesprek"
                        )
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
            // Context hint for empty conversation
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Start een gesprek",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Stel vragen, bespreek onderwerpen, of vraag om uitleg.\nIk onthoud de context van ons gesprek.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Suggestion chips
                        Text(
                            "Suggesties:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        SuggestionChip(
                            onClick = { viewModel.sendMessage("Kun je me helpen met een project? Ik leg eerst uit waar het over gaat.") },
                            label = { Text("📋 Help met project") }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SuggestionChip(
                            onClick = { viewModel.sendMessage("Ik wil iets leren over een onderwerp. Kun je me uitleggen hoe het werkt?") },
                            label = { Text("📚 Iets leren") }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SuggestionChip(
                            onClick = { viewModel.sendMessage("Ik heb een probleem en wil samen brainstormen over oplossingen.") },
                            label = { Text("💡 Brainstormen") }
                        )
                    }
                }
            } else {
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
                        ConversationBubble(
                            message = message,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
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
                        placeholder = { Text("Stel een vraag of praat verder...") },
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
                        singleLine = false,
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Voice input button
                    FloatingActionButton(
                        onClick = { launchVoiceInput() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Spraak invoer"
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
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
fun ConversationConnectionIndicator(state: OpenClawWebSocket.ConnectionState) {
    val (color, text) = when (state) {
        OpenClawWebSocket.ConnectionState.CONNECTED -> SuccessGreen to "●"
        OpenClawWebSocket.ConnectionState.CONNECTING -> WarningYellow to "○"
        OpenClawWebSocket.ConnectionState.HANDSHAKING -> WarningYellow to "○"
        OpenClawWebSocket.ConnectionState.RECONNECTING -> WarningYellow to "○"
        OpenClawWebSocket.ConnectionState.ERROR -> ErrorRed to "●"
        OpenClawWebSocket.ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant to "○"
    }
    
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun ConversationBubble(
    message: ConversationMessage,
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
            modifier = Modifier.widthIn(max = 320.dp),
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
