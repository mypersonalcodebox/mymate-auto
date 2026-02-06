package com.mymate.auto.util

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class to read calendar events and extract location information.
 */
object CalendarHelper {
    
    private const val TAG = "CalendarHelper"
    
    data class CalendarEvent(
        val id: Long,
        val title: String,
        val description: String?,
        val location: String?,
        val startTime: Long,
        val endTime: Long,
        val allDay: Boolean,
        val calendarName: String? = null
    ) {
        fun getFormattedTime(): String {
            val format = if (allDay) {
                SimpleDateFormat("EEE d MMM", Locale("nl", "NL"))
            } else {
                SimpleDateFormat("EEE d MMM HH:mm", Locale("nl", "NL"))
            }
            return format.format(Date(startTime))
        }
        
        fun getTimeRange(): String {
            if (allDay) return "Hele dag"
            val format = SimpleDateFormat("HH:mm", Locale("nl", "NL"))
            return "${format.format(Date(startTime))} - ${format.format(Date(endTime))}"
        }
        
        fun hasLocation(): Boolean = !location.isNullOrBlank()
    }
    
    /**
     * Get today's calendar events
     */
    fun getTodayEvents(context: Context): List<CalendarEvent> {
        return getEventsForDay(context, Calendar.getInstance())
    }
    
    /**
     * Get tomorrow's calendar events
     */
    fun getTomorrowEvents(context: Context): List<CalendarEvent> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return getEventsForDay(context, cal)
    }
    
    /**
     * Get events for a specific day
     */
    fun getEventsForDay(context: Context, date: Calendar): List<CalendarEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "No calendar permission")
            return emptyList()
        }
        
        val startOfDay = date.clone() as Calendar
        startOfDay.set(Calendar.HOUR_OF_DAY, 0)
        startOfDay.set(Calendar.MINUTE, 0)
        startOfDay.set(Calendar.SECOND, 0)
        startOfDay.set(Calendar.MILLISECOND, 0)
        
        val endOfDay = date.clone() as Calendar
        endOfDay.set(Calendar.HOUR_OF_DAY, 23)
        endOfDay.set(Calendar.MINUTE, 59)
        endOfDay.set(Calendar.SECOND, 59)
        endOfDay.set(Calendar.MILLISECOND, 999)
        
        return getEventsInRange(context, startOfDay.timeInMillis, endOfDay.timeInMillis)
    }
    
    /**
     * Get the next upcoming event (with optional location filter)
     */
    fun getNextEvent(context: Context, withLocationOnly: Boolean = false): CalendarEvent? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        
        val now = System.currentTimeMillis()
        val endTime = now + (7 * 24 * 60 * 60 * 1000L) // Next 7 days
        
        val events = getEventsInRange(context, now, endTime)
        
        return if (withLocationOnly) {
            events.firstOrNull { it.hasLocation() }
        } else {
            events.firstOrNull()
        }
    }
    
    /**
     * Get events in a time range
     */
    private fun getEventsInRange(context: Context, startMs: Long, endMs: Long): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME
        )
        
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(startMs.toString(), endMs.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"
        
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            cursor?.let {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val title = it.getString(1) ?: "Geen titel"
                    val description = it.getString(2)
                    val location = it.getString(3)
                    val start = it.getLong(4)
                    val end = it.getLong(5)
                    val allDay = it.getInt(6) == 1
                    val calendarName = it.getString(7)
                    
                    events.add(CalendarEvent(id, title, description, location, start, end, allDay, calendarName))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading calendar", e)
        } finally {
            cursor?.close()
        }
        
        return events
    }
    
    /**
     * Get events count for today
     */
    fun getTodayEventCount(context: Context): Int {
        return getTodayEvents(context).size
    }
}
