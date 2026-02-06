package com.mymate.auto.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*

/**
 * MainAutoScreen - Simple voice-first menu for MyMate Android Auto
 * 
 * Just 5 menu items, no complex state, no chat history.
 * Voice interaction is the primary feature.
 */
class MainAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        
        // 1. 🎤 PRAAT MET MYMATE - Voice Assistant (primary feature)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("🎤 PRAAT MET MYMATE")
                .addText("Start een gesprek met je AI-assistent")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(VoiceAssistantScreen(carContext))
                }
                .build()
        )
        
        // 2. 📍 Parking - Save/find parking location
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📍 Parking")
                .addText("Locatie opslaan of vinden")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(ParkingAutoScreen(carContext))
                }
                .build()
        )
        
        // 3. 📅 Agenda - View calendar (read-only)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("📅 Agenda")
                .addText("Bekijk je afspraken")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(AgendaAutoScreen(carContext))
                }
                .build()
        )
        
        // 4. ☀️ Briefing - Morning briefing (read-only)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("☀️ Briefing")
                .addText("Je dagelijkse overzicht")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(MorningBriefingScreen(carContext))
                }
                .build()
        )
        
        // 5. ⚙️ Instellingen - Settings
        listBuilder.addItem(
            Row.Builder()
                .setTitle("⚙️ Instellingen")
                .addText("Opties en configuratie")
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(SettingsAutoScreen(carContext))
                }
                .build()
        )
        
        return ListTemplate.Builder()
            .setTitle("MyMate")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }
}
