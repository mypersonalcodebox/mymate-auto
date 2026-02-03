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
    
    // === HOOFDMENU ACTIES (geen developer stuff hier) ===
    val mainActions = listOf(
        // Agenda & Planning
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
        
        // Communicatie
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
        
        // Informatie & Zoeken
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
            "news_tech", "📰", "Nieuws",
            "Wat is het belangrijkste nieuws vandaag?",
            ActionCategory.INFO
        ),
        QuickAction(
            "weather", "☀️", "Weer",
            "Wat is het weer vandaag en de komende uren? Moet ik ergens rekening mee houden?",
            ActionCategory.INFO
        ),
        
        // Navigatie & Locatie
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
        
        // Herinneringen & Notities
        QuickAction(
            "quick_note", "📝", "Snelle notitie", "",
            ActionCategory.GENERAL
        ),
        QuickAction(
            "remind_me", "⏰", "Herinner me", "",
            ActionCategory.GENERAL
        ),
        
        // Vrije vraag
        QuickAction(
            "free_chat", "💬", "Vrije vraag", "",
            ActionCategory.GENERAL
        )
    )
    
    // === DEVELOPER ACTIES (apart submenu) ===
    val developerActions = listOf(
        QuickAction(
            "start_task", "🛠️", "Start een taak", "",
            ActionCategory.GENERAL
        ),
        QuickAction(
            "github_issue", "📋", "GitHub issue maken", "",
            ActionCategory.GENERAL
        ),
        QuickAction(
            "build_feature", "🚀", "Bouw feature in repo",
            "Ik wil een nieuwe feature bouwen. ",
            ActionCategory.GENERAL
        ),
        QuickAction(
            "task_status", "🔄", "Status lopende taken",
            "Wat zijn de status updates van mijn lopende taken? Zijn er subagents actief? Wat loopt er?",
            ActionCategory.GENERAL
        ),
        QuickAction(
            "project_update", "📣", "Update over project", "",
            ActionCategory.GENERAL
        ),
        QuickAction(
            "code_review", "👀", "Review code",
            "Zijn er pull requests of code changes die ik moet reviewen?",
            ActionCategory.GENERAL
        ),
        QuickAction(
            "deploy_status", "📦", "Deploy status",
            "Wat is de status van recente deploys en builds?",
            ActionCategory.GENERAL
        ),
        QuickAction(
            "dev_idea", "💡", "Dev idee vastleggen", "",
            ActionCategory.GENERAL
        )
    )
    
    // Legacy: alle acties samen (voor backwards compatibility)
    val defaultActions = mainActions + developerActions
}
