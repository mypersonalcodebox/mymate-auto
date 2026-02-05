package com.mymate.auto.auto

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*

/**
 * Voice Input Screen for Android Auto
 * 
 * Uses Android Auto's built-in voice recognition through the SearchTemplate
 * or manual text input through the keyboard.
 */
class VoiceInputScreen(
    carContext: CarContext,
    private val actionContext: String? = null, // e.g., "github_issue", "start_task"
    private val onMessageReceived: (String) -> Unit
) : Screen(carContext) {
    
    private val TAG = "VoiceInputScreen"
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var inputText: String = ""
    
    override fun onGetTemplate(): Template {
        // Use SearchTemplate for voice input - Android Auto handles the voice recognition
        return SearchTemplate.Builder(
            object : SearchTemplate.SearchCallback {
                override fun onSearchTextChanged(searchText: String) {
                    Log.d(TAG, "Search text changed: $searchText")
                    inputText = searchText
                }
                
                override fun onSearchSubmitted(searchText: String) {
                    Log.d(TAG, "Search submitted: $searchText")
                    if (searchText.isNotBlank()) {
                        mainHandler.post {
                            onMessageReceived(searchText)
                            screenManager.pop()
                        }
                    }
                }
            }
        )
            .setHeaderAction(Action.BACK)
            .setShowKeyboardByDefault(false) // Let voice be primary
            .setInitialSearchText(inputText)
            .setSearchHint(getSearchHint())
            .build()
    }
    
    private fun getSearchHint(): String {
        return when (actionContext) {
            "start_task" -> "Beschrijf de taak..."
            "github_issue" -> "Beschrijf de issue..."
            "build_feature" -> "Welke feature wil je bouwen?"
            "project_update" -> "Wat is de update?"
            "dev_idea" -> "Beschrijf je idee..."
            "quick_note" -> "Wat wil je noteren?"
            "remind_me" -> "Waaraan moet ik je herinneren?"
            "search_info" -> "Waar zoek je naar?"
            "send_update" -> "Wat is je bericht?"
            else -> "Wat wil je vragen aan MyMate?"
        }
    }
}
