package com.mymate.auto.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mymate.auto.data.local.PreferencesManager
import com.mymate.auto.service.TtsManager
import com.mymate.auto.util.CalendarHelper
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Morning Briefing screen - read-only display of weather, agenda, and greeting.
 * User can tap "Lees voor" to have the briefing read aloud via TTS.
 */
class MorningBriefingScreen(carContext: CarContext) : Screen(carContext) {
    
    private val TAG = "MorningBriefingScreen"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = PreferencesManager(carContext)
    private val tts = TtsManager.getInstance(carContext)
    
    @Volatile private var isLoading = true
    @Volatile private var greeting = ""
    @Volatile private var weatherText = "Weer laden..."
    @Volatile private var agendaText = "Agenda laden..."
    @Volatile private var calendarEvents: List<CalendarHelper.CalendarEvent> = emptyList()
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()
    
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
        loadBriefing()
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
            // Load weather and calendar in parallel
            val weatherJob = async { fetchWeather() }
            val calendarJob = async { loadCalendar() }
            
            weatherText = weatherJob.await()
            val (events, text) = calendarJob.await()
            calendarEvents = events
            agendaText = text
            
            isLoading = false
            withContext(Dispatchers.Main) { invalidate() }
        }
    }
    
    private suspend fun fetchWeather(): String {
        return try {
            val location = prefs.getWeatherLocationSync()
            val request = Request.Builder()
                .url("https://wttr.in/$location?format=%c+%t+%w&lang=nl")
                .header("User-Agent", "MyMate-Auto/2.39")
                .build()
            
            withTimeout(10_000) {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()?.trim()
                        if (!body.isNullOrBlank()) "🌤️ $body" else "❌ Geen weerdata"
                    } else {
                        Log.w(TAG, "Weather HTTP ${response.code}")
                        "❌ Weer niet beschikbaar"
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Weather fetch timeout")
            "❌ Weer timeout"
        } catch (e: Exception) {
            Log.e(TAG, "Weather fetch failed", e)
            "❌ Weer niet beschikbaar"
        }
    }
    
    private fun loadCalendar(): Pair<List<CalendarHelper.CalendarEvent>, String> {
        return try {
            val events = CalendarHelper.getTodayEvents(carContext)
            val text = if (events.isEmpty()) {
                "📅 Geen afspraken vandaag"
            } else {
                val list = events.take(3).joinToString("\n") { 
                    "• ${it.getTimeRange()}: ${it.title}" 
                }
                val more = if (events.size > 3) "\n  +${events.size - 3} meer" else ""
                "📅 ${events.size} afspraak(en):\n$list$more"
            }
            events to text
        } catch (e: Exception) {
            Log.e(TAG, "Calendar load failed", e)
            emptyList<CalendarHelper.CalendarEvent>() to "📅 Agenda niet beschikbaar"
        }
    }
    
    private fun speakBriefing() {
        val today = SimpleDateFormat("EEEE d MMMM", Locale("nl", "NL")).format(Date())
        val speech = buildString {
            append(greeting.replace(Regex("[☀️🌤️🌆🌙]"), "").trim())
            append(". Het is vandaag $today. ")
            append(weatherText.replace(Regex("[🌤️❌]"), "").trim())
            append(". ")
            
            if (calendarEvents.isEmpty()) {
                append("Je hebt geen afspraken vandaag.")
            } else {
                append("Je hebt ${calendarEvents.size} afspraak")
                if (calendarEvents.size > 1) append("en")
                append(" vandaag. ")
                calendarEvents.firstOrNull()?.let {
                    append("De eerste is ${it.title} om ${it.getTimeRange()}.")
                }
            }
        }
        Log.d(TAG, "Speaking briefing: ${speech.take(80)}...")
        tts.speak(speech, stripMarkdown = false)
    }
    
    override fun onGetTemplate(): Template {
        if (isLoading) {
            return MessageTemplate.Builder("Briefing laden...")
                .setTitle("🌅 Ochtend Briefing")
                .setHeaderAction(Action.BACK)
                .build()
        }
        
        val today = SimpleDateFormat("EEEE d MMMM", Locale("nl", "NL")).format(Date())
        
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(greeting)
                    .addText(today)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Weer")
                    .addText(weatherText)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Agenda")
                    .addText(agendaText.take(100))
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("🔊 Lees voor")
                    .setOnClickListener { speakBriefing() }
                    .build()
            )
            .build()
        
        return PaneTemplate.Builder(pane)
            .setTitle("🌅 Ochtend Briefing")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
