package com.mymate.auto.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*

/**
 * "More" submenu screen - contains less frequently used features.
 * Android Auto has a 6 item limit per list, so we use this for overflow.
 */
class MoreAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        // Memories
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📝 Memories")
                .addText("Notities en herinneringen")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(MemoriesAutoScreen(carContext))
                }
                .build()
        )
        
        // Reminders
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⏰ Herinneringen")
                .addText("Timers en meldingen")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(RemindersAutoScreen(carContext))
                }
                .build()
        )
        
        // Morning Briefing
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🌅 Ochtend Briefing")
                .addText("Weer, agenda & overzicht")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(MorningBriefingScreen(carContext))
                }
                .build()
        )
        
        // Quick Actions
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⚡ Quick Actions")
                .addText("Snelle commando's")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(QuickActionsScreen(carContext))
                }
                .build()
        )
        
        // Developer
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🛠️ Developer")
                .addText("Taken en projecten")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(DeveloperActionsScreen(carContext))
                }
                .build()
        )
        
        return ListTemplate.Builder()
            .setTitle("📂 Meer")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
