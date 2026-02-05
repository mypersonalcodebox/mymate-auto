package com.mymate.auto.auto

import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.util.CalendarHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Morning Briefing screen - shows weather, agenda, and summary.
 * Reads the briefing aloud via TTS.
 */
class MorningBriefingScreen(carContext: CarContext) : Screen(carContext) {
    
    private val TAG = "MorningBriefingScreen"
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private val prefs = PreferencesManager(carContext)
    
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    
    @Volatile
    private var isLoading = true
    
    @Volatile
    private var weatherText: String = "Weer laden..."
    
    @Volatile
    private var agendaText: String = "Agenda laden..."
    
    @Volatile
    private var greeting: String = "Goedemorgen!"
    
    // Cached calendar events to avoid fetching twice
    @Volatile
    private var cachedEvents: List<CalendarHelper.CalendarEvent> = emptyList()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                supervisorJob.cancel()
                tts?.stop()
                tts?.shutdown()
            }
        })
        
        initTts()
        loadBriefing()
    }
    
    private fun initTts() {
        tts = TextToSpeech(carContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("nl", "NL"))
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && 
                          result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }
    
    private fun loadBriefing() {
        // Set greeting based on time of day
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greeting = when {
            hour < 6 -> "Goedenacht! 🌙"
            hour < 12 -> "Goedemorgen! ☀️"
            hour < 18 -> "Goedemiddag! 🌤️"
            else -> "Goedenavond! 🌆"
        }
        
        scope.launch {
            // Load weather
            try {
                weatherText = fetchWeather()
            } catch (e: Exception) {
                Log.e(TAG, "Weather fetch failed", e)
                weatherText = "❌ Weer niet beschikbaar"
            }
            
            // Load agenda and cache events for speakBriefing
            try {
                cachedEvents = CalendarHelper.getTodayEvents(carContext)
                agendaText = if (cachedEvents.isEmpty()) {
                    "📅 Geen afspraken vandaag"
                } else {
                    val eventList = cachedEvents.take(3).joinToString("\n") { event ->
                        "• ${event.getTimeRange()}: ${event.title}"
                    }
                    val more = if (cachedEvents.size > 3) "\n  +${cachedEvents.size - 3} meer" else ""
                    "📅 ${cachedEvents.size} afspraak(en):\n$eventList$more"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Agenda load failed", e)
                agendaText = "📅 Agenda niet beschikbaar"
                cachedEvents = emptyList()
            }
            
            isLoading = false
            withContext(Dispatchers.Main) {
                invalidate()
                
                // Read briefing aloud if TTS is ready and enabled
                scope.launch {
                    delay(500) // Small delay for screen to render
                    if (ttsReady && prefs.ttsEnabled.first()) {
                        speakBriefing()
                    }
                }
            }
        }
    }
    
    private suspend fun fetchWeather(): String {
        // Get weather location from preferences, default to Amsterdam
        val location = prefs.getWeatherLocationSync()
        
        val request = Request.Builder()
            .url("https://wttr.in/$location?format=%c+%t+%w&lang=nl")
            .header("User-Agent", "MyMate-Auto/2.39")
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim() ?: "Onbekend"
                    "🌤️ $body"
                } else {
                    "❌ Weer niet beschikbaar"
                }
            }
        } catch (e: Exception) {
            "❌ Weer niet beschikbaar"
        }
    }
    
    private fun speakBriefing() {
        val today = SimpleDateFormat("EEEE d MMMM", Locale("nl", "NL")).format(Date())
        val speech = buildString {
            append(greeting.replace(Regex("[☀️🌤️🌆🌙]"), ""))
            append(" Het is vandaag $today. ")
            append(weatherText.replace(Regex("[🌤️❌]"), "").trim())
            append(". ")
            
            // Use cached events instead of fetching again
            if (cachedEvents.isEmpty()) {
                append("Je hebt geen afspraken vandaag.")
            } else {
                append("Je hebt ${cachedEvents.size} afspraak${if (cachedEvents.size > 1) "en" else ""} vandaag. ")
                cachedEvents.firstOrNull()?.let { first ->
                    append("De eerste is ${first.title} om ${first.getTimeRange()}.")
                }
            }
        }
        
        tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "briefing")
    }
    
    override fun onGetTemplate(): Template {
        if (isLoading) {
            return MessageTemplate.Builder("Briefing laden...")
                .setTitle("🌅 Ochtend Briefing")
                .setHeaderAction(Action.BACK)
                .build()
        }
        
        val today = SimpleDateFormat("EEEE d MMMM", Locale("nl", "NL")).format(Date())
        
        val paneBuilder = Pane.Builder()
        
        // Greeting
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(greeting)
                .addText(today)
                .build()
        )
        
        // Weather
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("Weer")
                .addText(weatherText)
                .build()
        )
        
        // Agenda
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("Agenda")
                .addText(agendaText.take(100))
                .build()
        )
        
        // Speak button
        paneBuilder.addAction(
            Action.Builder()
                .setTitle("🔊 Lees voor")
                .setOnClickListener {
                    if (ttsReady) {
                        speakBriefing()
                    }
                }
                .build()
        )
        
        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("🌅 Ochtend Briefing")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
