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
            .setItemList(buildHintList())
            .build()
    }
    
    private fun buildHintList(): ItemList {
        val builder = ItemList.Builder()
        
        // Add contextual suggestions based on actionContext
        val suggestions = getContextualSuggestions()
        suggestions.forEach { (title, subtitle) ->
            builder.addItem(
                Row.Builder()
                    .setTitle(title)
                    .addText(subtitle)
                    .build()
            )
        }
        
        return builder.build()
    }
    
    private fun getContextualSuggestions(): List<Pair<String, String>> {
        return when (actionContext) {
            "conversation" -> listOf(
                "💬 Chat met MyMate" to "Stel een vraag of deel iets",
                "🔄 Blijf in gesprek" to "Ik onthoud de context"
            )
            "start_task" -> listOf(
                "💡 Tip: Beschrijf je taak" to "Bijv. 'Fix de login bug in de app'",
                "⏱️ Tijdsinschatting" to "Voeg optioneel een deadline toe"
            )
            "github_issue" -> listOf(
                "🐛 Bug melden" to "Beschrijf wat er mis gaat",
                "✨ Feature request" to "Beschrijf de gewenste functionaliteit"
            )
            "build_feature" -> listOf(
                "🏗️ Nieuwe feature" to "Beschrijf wat je wilt bouwen",
                "📋 Specificaties" to "Wees zo specifiek mogelijk"
            )
            "project_update" -> listOf(
                "📊 Voortgang delen" to "Wat heb je bereikt?",
                "🚧 Blokkades" to "Waar loop je tegenaan?"
            )
            "dev_idea" -> listOf(
                "💭 Idee pitchen" to "Beschrijf je concept",
                "🎯 Doel" to "Wat wil je ermee bereiken?"
            )
            "quick_note" -> listOf(
                "📝 Snelle notitie" to "Spreek je gedachte in",
                "🏷️ Tags" to "Voeg keywords toe voor later"
            )
            "remind_me" -> listOf(
                "⏰ Herinnering instellen" to "Bijv. 'Over 2 uur bellen met Jan'",
                "📅 Met datum" to "Bijv. 'Morgen om 9 uur meeting'"
            )
            "reminder_quick" -> listOf(
                "🎤 Spreek je herinnering in" to "Bijv. 'Boodschappen doen'",
                "💡 Kort en bondig" to "De tijd is al ingesteld"
            )
            "reminder_custom" -> listOf(
                "⏰ Tijd opgeven" to "Bijv. 'Over 30 minuten afspraak'",
                "📅 Morgen/vanavond" to "Bijv. 'Morgen Jan bellen'",
                "💡 Of alleen beschrijving" to "Standaard: over 30 minuten"
            )
            "search_info" -> listOf(
                "🔍 Zoeken" to "Stel je vraag",
                "📚 Documentatie" to "Zoek in je projecten"
            )
            "send_update" -> listOf(
                "💬 Bericht sturen" to "Typ of spreek je update",
                "📨 Naar wie?" to "Specificeer de ontvanger"
            )
            "add_memory" -> listOf(
                "📝 Nieuwe notitie" to "Spreek of typ je notitie",
                "💡 Tip" to "Bijv. 'Vergeet niet melk te kopen'",
                "🏷️ Auto-categorie" to "Je notitie wordt automatisch gecategoriseerd"
            )
            "edit_memory" -> listOf(
                "✏️ Bewerk notitie" to "Spreek of typ de nieuwe tekst",
                "💡 Tip" to "De oude tekst wordt vervangen"
            )
            "dev_command" -> listOf(
                "🛠️ Dev opdracht" to "Bijv. 'Check status van mijn taken'",
                "💡 Of vraag" to "Bijv. 'Maak een GitHub issue voor de login bug'"
            )
            else -> listOf(
                "💡 Tip: Spreek of typ je bericht" to "Druk op de microfoon om te beginnen",
                "🎤 Spraakinvoer" to "Houd de microfoon knop ingedrukt",
                "⌨️ Typen" to "Of gebruik het toetsenbord"
            )
        }
    }
    
    private fun getSearchHint(): String {
        return when (actionContext) {
            "conversation" -> "Wat wil je zeggen?"
            "start_task" -> "Beschrijf de taak..."
            "github_issue" -> "Beschrijf de issue..."
            "build_feature" -> "Welke feature wil je bouwen?"
            "project_update" -> "Wat is de update?"
            "dev_idea" -> "Beschrijf je idee..."
            "quick_note" -> "Wat wil je noteren?"
            "remind_me", "reminder_quick", "reminder_custom" -> "Waaraan moet ik je herinneren?"
            "search_info" -> "Waar zoek je naar?"
            "send_update" -> "Wat is je bericht?"
            "add_memory" -> "Wat wil je onthouden?"
            "edit_memory" -> "Wat is de nieuwe tekst?"
            "dev_command" -> "Wat is je dev opdracht?"
            else -> "Wat wil je vragen aan MyMate?"
        }
    }
}
