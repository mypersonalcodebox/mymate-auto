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
 * Simple read-only list with navigation for events with location.
 */
class AgendaAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    private val TAG = "AgendaAutoScreen"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ttsManager = TtsManager.getInstance(carContext)
    
    @Volatile private var isLoading = true
    @Volatile private var todayEvents: List<CalendarHelper.CalendarEvent> = emptyList()
    @Volatile private var tomorrowEvents: List<CalendarHelper.CalendarEvent> = emptyList()
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                ttsManager.stop()
                scope.cancel()
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
                todayEvents = emptyList()
                tomorrowEvents = emptyList()
            } finally {
                isLoading = false
                withContext(Dispatchers.Main) { invalidate() }
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
        
        // Empty state: no events at all
        if (todayEvents.isEmpty() && tomorrowEvents.isEmpty()) {
            return MessageTemplate.Builder("Geen afspraken voor vandaag of morgen")
                .setTitle("📅 Agenda")
                .setHeaderAction(Action.BACK)
                .setActionStrip(buildActionStrip())
                .build()
        }
        
        return ListTemplate.Builder()
            .setTitle("📅 Agenda")
            .setHeaderAction(Action.BACK)
            .setSingleList(buildEventList())
            .setActionStrip(buildActionStrip())
            .build()
    }
    
    private fun buildEventList(): ItemList {
        val builder = ItemList.Builder()
        var count = 0
        val maxItems = 6
        
        // Today's events
        if (todayEvents.isEmpty()) {
            builder.addItem(
                Row.Builder()
                    .setTitle("Vandaag")
                    .addText("Geen afspraken")
                    .build()
            )
            count++
        } else {
            todayEvents.forEach { event ->
                if (count >= maxItems) return@forEach
                builder.addItem(buildEventRow(event, prefix = null))
                count++
            }
        }
        
        // Tomorrow's events (only if space)
        if (count < maxItems) {
            if (tomorrowEvents.isEmpty()) {
                builder.addItem(
                    Row.Builder()
                        .setTitle("Morgen")
                        .addText("Geen afspraken")
                        .build()
                )
            } else {
                tomorrowEvents.forEach { event ->
                    if (count >= maxItems) return@forEach
                    builder.addItem(buildEventRow(event, prefix = "Morgen"))
                    count++
                }
            }
        }
        
        return builder.build()
    }
    
    private fun buildEventRow(event: CalendarHelper.CalendarEvent, prefix: String?): Row {
        val timeText = if (prefix != null) "$prefix ${event.getTimeRange()}" else event.getTimeRange()
        val locationIcon = if (event.hasLocation()) " 📍" else ""
        // Show calendar name if it's not the primary account
        val calendarSuffix = event.calendarName?.let { name ->
            if (name.contains("@") || name.lowercase() == "calendar") "" 
            else " • $name"
        } ?: ""
        
        return Row.Builder()
            .setTitle("$timeText$locationIcon")
            .addText("${event.title}$calendarSuffix")
            .apply {
                if (event.hasLocation()) {
                    setOnClickListener { navigateToEvent(event) }
                    setBrowsable(true)
                }
            }
            .build()
    }
    
    private fun buildActionStrip(): ActionStrip {
        return ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("🔊 Lees voor")
                    .setOnClickListener { speakAgenda() }
                    .build()
            )
            .build()
    }
    
    private fun speakAgenda() {
        val text = buildString {
            // Today
            if (todayEvents.isEmpty()) {
                append("Je hebt vandaag geen afspraken. ")
            } else {
                append("Vandaag heb je ${todayEvents.size} afspraak${if (todayEvents.size > 1) "en" else ""}. ")
                todayEvents.forEach { event ->
                    append("${event.getTimeRange()}, ${event.title}. ")
                    if (event.hasLocation()) append("Locatie: ${event.location}. ")
                }
            }
            
            // Tomorrow
            if (tomorrowEvents.isEmpty()) {
                append("Morgen heb je geen afspraken.")
            } else {
                append("Morgen heb je ${tomorrowEvents.size} afspraak${if (tomorrowEvents.size > 1) "en" else ""}. ")
                tomorrowEvents.forEach { event ->
                    append("${event.getTimeRange()}, ${event.title}. ")
                    if (event.hasLocation()) append("Locatie: ${event.location}. ")
                }
            }
        }
        
        ttsManager.speak(text, stripMarkdown = false)
    }
    
    private fun navigateToEvent(event: CalendarHelper.CalendarEvent) {
        val location = event.location ?: return
        
        try {
            val encoded = URLEncoder.encode(location, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded")).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            carContext.startCarApp(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Google Maps nav failed, trying geo URI", e)
            try {
                val encoded = URLEncoder.encode(location, "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                carContext.startCarApp(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Navigation failed for: $location", e2)
            }
        }
    }
}
