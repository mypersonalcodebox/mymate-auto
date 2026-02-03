package com.mymate.auto.data.model

data class QuickAction(
    val id: String,
    val emoji: String,
    val title: String,
    val query: String,
    val category: ActionCategory = ActionCategory.GENERAL,
    var usageCount: Int = 0,
    var lastUsed: Long = 0
)

enum class ActionCategory {
    CALENDAR,
    COMMUNICATION,
    WEATHER,
    NAVIGATION,
    REMINDERS,
    INFO,
    MEDIA,
    GENERAL
}

object QuickActions {
    val defaultActions = listOf(
        QuickAction("agenda_today", "📅", "Agenda vandaag", "Wat staat er vandaag op mijn agenda?", ActionCategory.CALENDAR),
        QuickAction("unread_mail", "📧", "Ongelezen mail", "Heb ik belangrijke ongelezen emails?", ActionCategory.COMMUNICATION),
        QuickAction("weather", "☀️", "Weer", "Wat is het weer vandaag en morgen?", ActionCategory.WEATHER),
        QuickAction("going_home", "🏠", "Onderweg naar huis", "Ik ben onderweg naar huis, iets dat ik moet weten?", ActionCategory.NAVIGATION),
        QuickAction("remind_me", "📝", "Herinner me...", "Herinner me over 30 minuten om ", ActionCategory.REMINDERS),
        QuickAction("shopping_list", "🛒", "Boodschappenlijst", "Wat staat er op mijn boodschappenlijst?", ActionCategory.REMINDERS),
        QuickAction("news_briefing", "📰", "Nieuws briefing", "Geef me een kort nieuws overzicht van vandaag", ActionCategory.INFO),
        QuickAction("expenses_today", "💰", "Uitgaven vandaag", "Wat heb ik vandaag uitgegeven?", ActionCategory.INFO),
        QuickAction("traffic", "🚗", "Verkeer", "Hoe is het verkeer richting werk?", ActionCategory.NAVIGATION),
        QuickAction("call_contact", "📞", "Bel contact", "Bel ", ActionCategory.COMMUNICATION),
        QuickAction("send_message", "💬", "Stuur bericht", "Stuur een WhatsApp bericht naar ", ActionCategory.COMMUNICATION),
        QuickAction("next_appointment", "⏰", "Volgende afspraak", "Wat is mijn volgende afspraak?", ActionCategory.CALENDAR),
        QuickAction("play_music", "🎵", "Speel muziek", "Speel mijn favoriete playlist", ActionCategory.MEDIA),
        QuickAction("where_am_i", "📍", "Waar ben ik", "Waar ben ik nu en wat is er in de buurt?", ActionCategory.NAVIGATION),
        QuickAction("week_overview", "📋", "Week overzicht", "Geef me een overzicht van deze week", ActionCategory.CALENDAR),
        QuickAction("commute_time", "⏱️", "Reistijd", "Hoe lang duurt het naar mijn volgende afspraak?", ActionCategory.NAVIGATION),
        QuickAction("battery_check", "🔋", "Check systemen", "Hoe staat het met alle systemen thuis?", ActionCategory.INFO),
        QuickAction("morning_briefing", "☕", "Ochtend briefing", "Geef me mijn ochtend briefing", ActionCategory.INFO),
        QuickAction("evening_summary", "🌙", "Avond samenvatting", "Geef me een samenvatting van vandaag", ActionCategory.INFO),
        QuickAction("parking_spot", "🅿️", "Parkeerplaats", "Onthoud deze parkeerplaats", ActionCategory.NAVIGATION),
        QuickAction("free_chat", "💭", "Vrije vraag", "", ActionCategory.GENERAL)
    )
}
