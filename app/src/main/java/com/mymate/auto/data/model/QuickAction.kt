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
    INFO,
    NAVIGATION,
    GENERAL
}

object QuickActions {
    val defaultActions = listOf(
        // Agenda & Planning
        QuickAction("agenda_today", "📅", "Wat staat er vandaag?", "Wat staat er vandaag op mijn agenda?", ActionCategory.CALENDAR),
        QuickAction("next_appointment", "⏰", "Volgende afspraak", "Wat is mijn volgende afspraak en wanneer moet ik weg?", ActionCategory.CALENDAR),
        QuickAction("week_overview", "📋", "Deze week", "Geef me een overzicht van wat er deze week gepland staat", ActionCategory.CALENDAR),
        
        // Communicatie
        QuickAction("important_mail", "📧", "Belangrijke mail?", "Heb ik belangrijke of urgente emails die ik moet weten?", ActionCategory.COMMUNICATION),
        QuickAction("unread_messages", "💬", "Gemiste berichten", "Heb ik belangrijke berichten gemist?", ActionCategory.COMMUNICATION),
        
        // Informatie & Zoeken
        QuickAction("search_info", "🔍", "Zoek informatie", "Zoek informatie over ", ActionCategory.INFO),
        QuickAction("tell_story", "📖", "Vertel een verhaal", "Vertel me een interessant verhaal of feitje", ActionCategory.INFO),
        QuickAction("news_briefing", "📰", "Nieuws", "Wat is het belangrijkste nieuws vandaag?", ActionCategory.INFO),
        QuickAction("weather", "☀️", "Weer", "Wat is het weer vandaag en moet ik rekening houden met iets?", ActionCategory.INFO),
        
        // Navigatie & Locatie  
        QuickAction("going_home", "🏠", "Naar huis", "Ik ga naar huis, zijn er files of moet ik iets weten?", ActionCategory.NAVIGATION),
        QuickAction("route_to", "🗺️", "Route naar...", "Zoek de beste route naar ", ActionCategory.NAVIGATION),
        QuickAction("traffic_info", "🚗", "Verkeer check", "Hoe is het verkeer op mijn route?", ActionCategory.NAVIGATION),
        QuickAction("nearby", "📍", "Wat is hier?", "Wat is er interessant in de buurt?", ActionCategory.NAVIGATION),
        
        // Herinneringen
        QuickAction("remind_later", "⏰", "Herinner me straks", "Herinner me over 30 minuten aan ", ActionCategory.GENERAL),
        QuickAction("remind_home", "🏡", "Herinner thuis", "Herinner me als ik thuis ben om ", ActionCategory.GENERAL),
        
        // Vrije vraag (altijd onderaan)
        QuickAction("free_chat", "💭", "Stel een vraag", "", ActionCategory.GENERAL)
    )
}
