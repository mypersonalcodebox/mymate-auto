package com.mymate.auto.ui.setup

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mymate.auto.ui.theme.PrimaryBlue
import com.mymate.auto.ui.theme.SuccessGreen
import com.mymate.auto.ui.theme.ErrorRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showToken by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MyMate Instellen") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Welcome icon
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = PrimaryBlue
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Welcome text
            Text(
                text = "Welkom bij MyMate",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Verbind met je OpenClaw Gateway om te beginnen",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Gateway URL field
            OutlinedTextField(
                value = uiState.gatewayUrl,
                onValueChange = { viewModel.updateGatewayUrl(it) },
                label = { Text("Gateway URL") },
                placeholder = { Text("ws://your-gateway:18789") },
                leadingIcon = {
                    Icon(Icons.Default.Link, contentDescription = null)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.connectionStatus == ConnectionStatus.ERROR
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Auth Token field
            OutlinedTextField(
                value = uiState.authToken,
                onValueChange = { viewModel.updateAuthToken(it) },
                label = { Text("Auth Token") },
                placeholder = { Text("Je authenticatie token") },
                leadingIcon = {
                    Icon(Icons.Default.Key, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showToken) "Verberg" else "Toon"
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.connectionStatus == ConnectionStatus.ERROR
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Help text
            Text(
                text = "De Gateway URL en Auth Token vind je in je OpenClaw configuratie",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Connection status
            AnimatedVisibility(
                visible = uiState.connectionStatus != ConnectionStatus.IDLE,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (uiState.connectionStatus) {
                            ConnectionStatus.TESTING -> MaterialTheme.colorScheme.surfaceVariant
                            ConnectionStatus.SUCCESS -> SuccessGreen.copy(alpha = 0.15f)
                            ConnectionStatus.ERROR -> ErrorRed.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (uiState.connectionStatus) {
                            ConnectionStatus.TESTING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Verbinding testen...")
                            }
                            ConnectionStatus.SUCCESS -> {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Verbinding geslaagd!",
                                    color = SuccessGreen
                                )
                            }
                            ConnectionStatus.ERROR -> {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = ErrorRed
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    uiState.errorMessage ?: "Verbinding mislukt",
                                    color = ErrorRed
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Test Connection button
            OutlinedButton(
                onClick = { viewModel.testConnection() },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.gatewayUrl.isNotBlank() && 
                          uiState.authToken.isNotBlank() &&
                          uiState.connectionStatus != ConnectionStatus.TESTING
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Verbinding")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Save button
            Button(
                onClick = { 
                    viewModel.saveConfiguration()
                    onSetupComplete()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.connectionStatus == ConnectionStatus.SUCCESS,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                )
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Opslaan en Starten")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Je kunt deze instellingen later wijzigen via het instellingen menu in de app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
