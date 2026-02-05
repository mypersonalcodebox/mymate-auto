package com.mymate.auto.auto

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mymate.auto.util.CalendarHelper
import kotlinx.coroutines.*
import java.net.URLEncoder

/**
 * Agenda screen for Android Auto - shows today's events with navigation option.
 */
class AgendaAutoScreen(carContext: CarContext) : Screen(carContext) {
    
    private val TAG = "AgendaAutoScreen"
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    
    @Volatile
    private var isLoading = true
    
    @Volatile
    private var todayEvents: List<CalendarHelper.CalendarEvent> = emptyList()
    
    @Volatile
    private var tomorrowEvents: List<CalendarHelper.CalendarEvent> = emptyList()
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
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
        val maxItems = 6 // Android Auto limit!
        
        // Next event with location (if any)
        val nextWithLocation = CalendarHelper.getNextEvent(carContext, withLocationOnly = true)
        if (nextWithLocation != null && itemCount < maxItems) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("🧭 Navigeer: ${nextWithLocation.title}")
                    .addText("📍 ${nextWithLocation.location}")
                    .setBrowsable(true)
                    .setOnClickListener {
                        navigateToEvent(nextWithLocation)
                    }
                    .build()
            )
            itemCount++
        }
        
        // Today's events (simplified - no header, just events)
        if (todayEvents.isEmpty() && itemCount < maxItems) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("📅 Geen afspraken vandaag")
                    .build()
            )
            itemCount++
        } else {
            todayEvents.take(maxItems - itemCount).forEach { event ->
                if (itemCount >= maxItems) return@forEach
                val locationIcon = if (event.hasLocation()) "📍" else ""
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("${event.getTimeRange()} $locationIcon")
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
        
        // Tomorrow events (only if space left)
        if (tomorrowEvents.isNotEmpty() && itemCount < maxItems) {
            tomorrowEvents.take(maxItems - itemCount).forEach { event ->
                if (itemCount >= maxItems) return@forEach
                val locationIcon = if (event.hasLocation()) "📍" else ""
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("Morgen ${event.getTimeRange()} $locationIcon")
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
        
        return ListTemplate.Builder()
            .setTitle("📅 Agenda")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
    
    private fun navigateToEvent(event: CalendarHelper.CalendarEvent) {
        val location = event.location ?: return
        
        try {
            // Try Google Maps with the address
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
