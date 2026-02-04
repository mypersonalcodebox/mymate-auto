package com.mymate.auto.ui.crashlogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymate.auto.MyMateApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as MyMateApplication
    
    var crashLogs by remember { mutableStateOf(MyMateApplication.getAllCrashLogs(application)) }
    var selectedLog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Crash logs wissen?") },
            text = { Text("Alle ${crashLogs.size} crash logs worden verwijderd.") },
            confirmButton = {
                TextButton(onClick = {
                    MyMateApplication.clearCrashLogs(application)
                    crashLogs = emptyList()
                    selectedLog = null
                    showClearDialog = false
                    Toast.makeText(context, "Crash logs gewist", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Wissen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Annuleren")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedLog?.first ?: "Crash Logs") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedLog != null) {
                            selectedLog = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug")
                    }
                },
                actions = {
                    if (selectedLog != null) {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", selectedLog!!.second))
                            Toast.makeText(context, "Gekopieerd!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, "Kopiëren")
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "MyMate Crash Log - ${selectedLog!!.first}")
                                putExtra(Intent.EXTRA_TEXT, selectedLog!!.second)
                            }
                            context.startActivity(Intent.createChooser(intent, "Deel crash log"))
                        }) {
                            Icon(Icons.Default.Share, "Delen")
                        }
                    } else if (crashLogs.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, "Wissen")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (selectedLog != null) {
            // Show selected crash log
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = selectedLog!!.second,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        } else if (crashLogs.isEmpty()) {
            // No crashes
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Geen crashes!")
                    Text(
                        "De app draait stabiel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // List of crash logs
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(crashLogs) { (filename, content) ->
                    CrashLogCard(
                        filename = filename,
                        preview = content.lines().take(5).joinToString("\n"),
                        onClick = { selectedLog = filename to content }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashLogCard(
    filename: String,
    preview: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = filename.removePrefix("crash_").removeSuffix(".txt"),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
