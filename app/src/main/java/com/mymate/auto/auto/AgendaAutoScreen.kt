package com.mymate.auto.auto

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mymate.auto.service.TtsManager
import com.mymate.auto.util.CalendarHelper
import kotlinx.coroutines.*
import java.net.URLEncoder

/**
 * Agenda screen for Android Auto - shows today's and tomorrow's events.
 * Simple list view with navigation option for events with location.
 */
class AgendaAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    private val TAG = "AgendaAutoScreen"
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val ttsManager = TtsManager.getInstance(carContext)
    
    @Volatile
    private var isLoading = true
    
    @Volatile
    private var todayEvents: List<CalendarHelper.CalendarEvent> = emptyList()
    
    @Volatile
    private var tomorrowEvents: List<CalendarHelper.CalendarEvent> = emptyList()
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                ttsManager.stop()
                supervisorJob.cancel()
            }
        })
        loadEvents()
    }
    
    private fun loadEvents() {
        scope.launch {
            try {
                todayEvents = CalendarHelper.getTodayEvents(carContext)
                tomorrowEvents = CalendarHelper.getTomorrowEvents(carContext)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading events", e)
            }
            isLoading = false
            withContext(Dispatchers.Main) {
                invalidate()
            }
        }
    }
    
    override fun onGetTemplate(): Template {
        if (isLoading) {
            return MessageTemplate.Builder("Agenda laden...")
                .setTitle("📅 Agenda")
                .setHeaderAction(Action.BACK)
                .build()
        }
        
        val listBuilder = ItemList.Builder()
        var itemCount = 0
        val maxItems = 6 // Android Auto limit
        
        // Today's events
        if (todayEvents.isEmpty()) {
            if (itemCount < maxItems) {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("Vandaag")
                        .addText("Geen afspraken")
                        .build()
                )
                itemCount++
            }
        } else {
            todayEvents.forEach { event ->
                if (itemCount >= maxItems) return@forEach
                val locationIcon = if (event.hasLocation()) " 📍" else ""
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("${event.getTimeRange()}$locationIcon")
                        .addText(event.title)
                        .apply {
                            if (event.hasLocation()) {
                                setOnClickListener { navigateToEvent(event) }
                                setBrowsable(true)
                            }
                        }
                        .build()
                )
                itemCount++
            }
        }
        
        // Tomorrow's events (only if space left)
        if (itemCount < maxItems && tomorrowEvents.isNotEmpty()) {
            tomorrowEvents.forEach { event ->
                if (itemCount >= maxItems) return@forEach
                val locationIcon = if (event.hasLocation()) " 📍" else ""
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("Morgen ${event.getTimeRange()}$locationIcon")
                        .addText(event.title)
                        .apply {
                            if (event.hasLocation()) {
                                setOnClickListener { navigateToEvent(event) }
                                setBrowsable(true)
                            }
                        }
                        .build()
                )
                itemCount++
            }
        } else if (itemCount < maxItems && tomorrowEvents.isEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Morgen")
                    .addText("Geen afspraken")
                    .build()
            )
            itemCount++
        }
        
        // Build ActionStrip with TTS button
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("🔊 Lees voor")
                    .setOnClickListener { speakAgenda() }
                    .build()
            )
            .build()
        
        return ListTemplate.Builder()
            .setTitle("📅 Agenda")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .setActionStrip(actionStrip)
            .build()
    }
    
    /**
     * Speak the agenda using TTS
     */
    private fun speakAgenda() {
        val sb = StringBuilder()
        
        // Today's events
        if (todayEvents.isEmpty()) {
            sb.append("Je hebt vandaag geen afspraken. ")
        } else {
            sb.append("Vandaag heb je ${todayEvents.size} afspraak${if (todayEvents.size > 1) "en" else ""}. ")
            todayEvents.forEach { event ->
                sb.append("${event.getTimeRange()}, ${event.title}. ")
                if (event.hasLocation()) {
                    sb.append("Locatie: ${event.location}. ")
                }
            }
        }
        
        // Tomorrow's events
        if (tomorrowEvents.isEmpty()) {
            sb.append("Morgen heb je geen afspraken.")
        } else {
            sb.append("Morgen heb je ${tomorrowEvents.size} afspraak${if (tomorrowEvents.size > 1) "en" else ""}. ")
            tomorrowEvents.forEach { event ->
                sb.append("${event.getTimeRange()}, ${event.title}. ")
                if (event.hasLocation()) {
                    sb.append("Locatie: ${event.location}. ")
                }
            }
        }
        
        ttsManager.speak(sb.toString(), stripMarkdown = false)
    }
    
    private fun navigateToEvent(event: CalendarHelper.CalendarEvent) {
        val location = event.location ?: return
        
        try {
            // Try Google Maps navigation
            val encodedLocation = URLEncoder.encode(location, "UTF-8")
            val uri = Uri.parse("google.navigation:q=$encodedLocation")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            carContext.startCarApp(intent)
        } catch (e: Exception) {
            // Fallback to geo URI
            try {
                val encodedLocation = URLEncoder.encode(location, "UTF-8")
                val uri = Uri.parse("geo:0,0?q=$encodedLocation")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                carContext.startCarApp(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Navigation failed for: $location", e2)
            }
        }
    }
}
