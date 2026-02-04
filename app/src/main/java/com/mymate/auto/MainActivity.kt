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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.ui.chat.ChatScreen
import com.mymate.auto.ui.crashlogs.CrashLogsScreen
import com.mymate.auto.ui.settings.SettingsScreen
import com.mymate.auto.ui.theme.MyMateAutoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val preferencesManager = PreferencesManager(this)
        
        setContent {
            val darkMode by preferencesManager.darkMode.collectAsState(
                initial = runBlocking { preferencesManager.darkMode.first() }
            )
            
            MyMateAutoTheme(darkTheme = darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MyMateApp()
                }
            }
        }
    }
}

@Composable
fun MyMateApp() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "chat"
    ) {
        composable("chat") {
            ChatScreen(
                onNavigateToSettings = {
                    navController.navigate("settings")
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
