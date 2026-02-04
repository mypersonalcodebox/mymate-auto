package com.mymate.auto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
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
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val preferencesManager = PreferencesManager(this)
        
        // Check if app is configured
        val isConfigured = runBlocking { preferencesManager.getIsConfiguredSync() }
        
        setContent {
            val darkMode by preferencesManager.darkMode.collectAsState(
                initial = runBlocking { preferencesManager.darkMode.first() }
            )
            
            MyMateAutoTheme(darkTheme = darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MyMateApp(startDestination = if (isConfigured) "chat" else "setup")
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
