package com.mymate.auto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.ui.chat.ChatScreen
import com.mymate.auto.ui.conversation.ConversationScreen
import com.mymate.auto.ui.crashlogs.CrashLogsScreen
import com.mymate.auto.ui.memories.MemoriesScreen
import com.mymate.auto.ui.parking.ParkingScreen
import com.mymate.auto.ui.reminders.RemindersScreen
import com.mymate.auto.ui.settings.SettingsScreen
import com.mymate.auto.ui.setup.SetupScreen
import com.mymate.auto.ui.theme.MyMateAutoTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val preferencesManager = PreferencesManager(this)
        
        setContent {
            // Loading state - avoid runBlocking on main thread (ANR risk)
            var isLoading by remember { mutableStateOf(true) }
            var isConfigured by remember { mutableStateOf(false) }
            var darkMode by remember { mutableStateOf(false) }
            
            // Load preferences asynchronously
            LaunchedEffect(Unit) {
                isConfigured = preferencesManager.getIsConfiguredSync()
                darkMode = preferencesManager.darkMode.first()
                isLoading = false
            }
            
            // Keep darkMode updated when it changes
            LaunchedEffect(Unit) {
                preferencesManager.darkMode.collect { darkMode = it }
            }
            
            MyMateAutoTheme(darkTheme = darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isLoading) {
                        // Show loading indicator while preferences are being loaded
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        MyMateApp(startDestination = if (isConfigured) "chat" else "setup")
                    }
                }
            }
        }
    }
}

@Composable
fun MyMateApp(startDestination: String = "chat") {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("setup") {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate("chat") {
                        popUpTo("setup") { inclusive = true }
                    }
                }
            )
        }
        
        composable("chat") {
            ChatScreen(
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToConversation = {
                    navController.navigate("conversation")
                },
                onNavigateToParking = {
                    navController.navigate("parking")
                },
                onNavigateToMemories = {
                    navController.navigate("memories")
                },
                onNavigateToReminders = {
                    navController.navigate("reminders")
                }
            )
        }
        
        composable("conversation") {
            ConversationScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("parking") {
            ParkingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("memories") {
            MemoriesScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("reminders") {
            RemindersScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCrashLogs = {
                    navController.navigate("crashlogs")
                }
            )
        }
        
        composable("crashlogs") {
            CrashLogsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
