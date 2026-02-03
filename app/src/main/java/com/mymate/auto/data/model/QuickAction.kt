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
    DEV,
    CALENDAR,
    COMMUNICATION,
    INFO,
    NAVIGATION,
    GENERAL
}

object QuickActions {
    val defaultActions = listOf(
        // === DEVELOPER ACTIES (prioriteit voor Ferron) ===
        
        // Taken & Project Management
        QuickAction(
            "start_task", "🛠️", "Start een taak", "",
            ActionCategory.DEV
        ),
        QuickAction(
            "task_status", "🔄", "Status lopende taken",
            "Wat zijn de status updates van mijn lopende taken? Zijn er subagents actief? Wat loopt er?",
            ActionCategory.DEV
        ),
        QuickAction(
            "github_issue", "📋", "GitHub issue maken", "",
            ActionCategory.DEV
        ),
        QuickAction(
            "build_feature", "🚀", "Bouw feature",
            "Ik wil een nieuwe feature bouwen. ",
            ActionCategory.DEV
        ),
        QuickAction(
            "code_review", "👀", "Review code",
            "Zijn er pull requests of code changes die ik moet reviewen?",
            ActionCategory.DEV
        ),
        QuickAction(
            "deploy_status", "📦", "Deploy status",
            "Wat is de status van recente deploys en builds?",
            ActionCategory.DEV
        ),
        
        // === AGENDA & PLANNING ===
        QuickAction(
            "agenda_today", "📅", "Agenda vandaag",
            "Wat staat er vandaag op mijn agenda? Geef een kort overzicht.",
            ActionCategory.CALENDAR
        ),
        QuickAction(
            "next_appointment", "⏰", "Volgende afspraak",
            "Wat is mijn volgende afspraak en wanneer moet ik weg?",
            ActionCategory.CALENDAR
        ),
        QuickAction(
            "week_planning", "📋", "Weekplanning",
            "Geef me een overzicht van wat er deze week gepland staat, inclusief deadlines.",
            ActionCategory.CALENDAR
        ),
        
        // === COMMUNICATIE ===
        QuickAction(
            "mail_check", "📧", "Check mail",
            "Heb ik belangrijke of urgente emails? Geef me een korte samenvatting van de belangrijkste.",
            ActionCategory.COMMUNICATION
        ),
        QuickAction(
            "unread_messages", "💬", "Gemiste berichten",
            "Heb ik belangrijke berichten gemist op Telegram of andere kanalen?",
            ActionCategory.COMMUNICATION
        ),
        QuickAction(
            "send_update", "📣", "Stuur update", "",
            ActionCategory.COMMUNICATION
        ),
        
        // === INFORMATIE & ZOEKEN ===
        QuickAction(
            "search_info", "🔍", "Zoek informatie", "",
            ActionCategory.INFO
        ),
        QuickAction(
            "tell_story", "📖", "Verhaal/feitje",
            "Vertel me een interessant verhaal of leuk feitje. Iets om de rit te veraangenamen.",
            ActionCategory.INFO
        ),
        QuickAction(
            "news_tech", "📰", "Tech nieuws",
            "Wat is het belangrijkste tech nieuws vandaag? Focus op AI, development en startups.",
            ActionCategory.INFO
        ),
        QuickAction(
            "weather", "☀️", "Weer",
            "Wat is het weer vandaag en de komende uren? Moet ik ergens rekening mee houden?",
            ActionCategory.INFO
        ),
        QuickAction(
            "explain", "🧠", "Leg uit", "",
            ActionCategory.INFO
        ),
        
        // === NAVIGATIE & LOCATIE ===
        QuickAction(
            "going_home", "🏠", "Naar huis",
            "Ik ga naar huis. Zijn er files of vertragingen op de route? Moet ik iets weten?",
            ActionCategory.NAVIGATION
        ),
        QuickAction(
            "traffic_check", "🚗", "Verkeer check",
            "Hoe is het verkeer? Zijn er files of ongelukken die ik moet vermijden?",
            ActionCategory.NAVIGATION
        ),
        QuickAction(
            "find_place", "📍", "Zoek locatie", "",
            ActionCategory.NAVIGATION
        ),
        
        // === HERINNERINGEN & NOTITIES ===
        QuickAction(
            "quick_note", "📝", "Snelle notitie", "",
            ActionCategory.GENERAL
        ),
        QuickAction(
            "remind_me", "⏰", "Herinner me", "",
            ActionCategory.GENERAL
        ),
        QuickAction(
            "idea", "💡", "Idee vastleggen", "",
            ActionCategory.GENERAL
        ),
        
        // === VRIJE VRAAG (altijd onderaan) ===
        QuickAction(
            "free_chat", "💬", "Vrije vraag", "",
            ActionCategory.GENERAL
        )
    )
}
