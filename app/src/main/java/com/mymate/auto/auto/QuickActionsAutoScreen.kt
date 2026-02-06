package com.mymate.auto.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*

/**
 * Quick Actions submenu for VoiceAssistantScreen
 * 
 * Contains template prompts that open voice input with a prefix.
 */
class QuickActionsAutoScreen(
    carContext: CarContext,
    private val onActionSelected: (prefix: String, context: String) -> Unit
) : Screen(carContext) {
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        // Template actions - user speaks to complete
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📝 Discussie starten")
                .addText("\"Laten we discussiëren over...\"")
                .setOnClickListener { 
                    onActionSelected("Laten we discussiëren over", "discussion")
                    screenManager.pop()
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("💡 Brainstormen")
                .addText("\"Help me brainstormen over...\"")
                .setOnClickListener { 
                    onActionSelected("Help me brainstormen over", "brainstorm")
                    screenManager.pop()
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🔍 Research")
                .addText("\"Zoek informatie over...\"")
                .setOnClickListener { 
                    onActionSelected("Zoek informatie over", "research")
                    screenManager.pop()
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("💻 Code hulp")
                .addText("\"Help me met code voor...\"")
                .setOnClickListener { 
                    onActionSelected("Help me met code voor", "code")
                    screenManager.pop()
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📋 Samenvatten")
                .addText("\"Vat dit samen:...\"")
                .setOnClickListener { 
                    onActionSelected("Vat dit samen:", "summarize")
                    screenManager.pop()
                }
                .build()
        )
        
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎯 Plan maken")
                .addText("\"Maak een plan voor...\"")
                .setOnClickListener { 
                    onActionSelected("Maak een plan voor", "plan")
                    screenManager.pop()
                }
                .build()
        )
        
        return ListTemplate.Builder()
            .setTitle("⚡ Snelle acties")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
