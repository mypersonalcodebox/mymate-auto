package com.mymate.auto.util

import com.mymate.auto.data.model.MemoryCategory

/**
 * Utility to detect memory-worthy content in messages
 * 
 * Recognizes patterns like:
 * - "onthoud dat..."
 * - "vergeet niet..."
 * - "mijn X is Y"
 * - "ik parkeer op..."
 */
object MemoryDetector {
    
    // Patterns that indicate user wants to save something
    private val rememberPatterns = listOf(
        Regex("(?i)^onthoud\\s+(?:dat\\s+)?(.+)", RegexOption.DOT_MATCHES_ALL),
        Regex("(?i)^remember\\s+(?:that\\s+)?(.+)", RegexOption.DOT_MATCHES_ALL),
        Regex("(?i)^vergeet\\s+niet\\s+(?:dat\\s+)?(.+)", RegexOption.DOT_MATCHES_ALL),
        Regex("(?i)^don'?t\\s+forget\\s+(?:that\\s+)?(.+)", RegexOption.DOT_MATCHES_ALL),
        Regex("(?i)^noteer\\s+(?:dat\\s+)?(.+)", RegexOption.DOT_MATCHES_ALL),
        Regex("(?i)^sla\\s+op\\s+(?:dat\\s+)?(.+)", RegexOption.DOT_MATCHES_ALL)
    )
    
    // Patterns that indicate personal info
    private val personalInfoPatterns = listOf(
        Regex("(?i)^mijn\\s+(\\w+)\\s+is\\s+(.+)"),
        Regex("(?i)^my\\s+(\\w+)\\s+is\\s+(.+)"),
        Regex("(?i)^ik\\s+(?:ben|heb|hou van|werk bij)\\s+(.+)"),
        Regex("(?i)^i\\s+(?:am|have|like|work at)\\s+(.+)")
    )
    
    // Patterns for parking/location
    private val locationPatterns = listOf(
        Regex("(?i)(?:ik\\s+)?(?:sta\\s+)?(?:geparkeerd\\s+)?(?:op|in|bij)\\s+(?:plek\\s+|vak\\s+)?(.+)"),
        Regex("(?i)parked\\s+(?:at|in|on)\\s+(.+)"),
        Regex("(?i)parkeerplaats\\s+(?:is\\s+)?(.+)"),
        Regex("(?i)parking\\s+spot\\s+(?:is\\s+)?(.+)")
    )
    
    // Patterns for todos
    private val todoPatterns = listOf(
        Regex("(?i)^(?:ik\\s+)?moet\\s+(?:nog\\s+)?(.+)"),
        Regex("(?i)^(?:i\\s+)?(?:need\\s+to|have\\s+to|must)\\s+(.+)"),
        Regex("(?i)^todo[:\\s]+(.+)"),
        Regex("(?i)^te\\s+doen[:\\s]+(.+)")
    )
    
    /**
     * Check if a message should be saved as a memory
     * Returns the content to save and suggested category, or null if not memory-worthy
     */
    fun detectMemory(message: String): MemoryDetectionResult? {
        val trimmed = message.trim()
        
        // Check explicit "remember" patterns
        for (pattern in rememberPatterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val content = match.groupValues.getOrNull(1)?.trim() ?: trimmed
                return MemoryDetectionResult(
                    content = content,
                    category = categorizeContent(content),
                    confidence = 1.0f
                )
            }
        }
        
        // Check location/parking patterns
        for (pattern in locationPatterns) {
            val match = pattern.find(trimmed)
            if (match != null && trimmed.length < 100) {
                return MemoryDetectionResult(
                    content = trimmed,
                    category = MemoryCategory.LOCATION,
                    confidence = 0.8f
                )
            }
        }
        
        // Check todo patterns
        for (pattern in todoPatterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val content = match.groupValues.getOrNull(1)?.trim() ?: trimmed
                return MemoryDetectionResult(
                    content = content,
                    category = MemoryCategory.TODO,
                    confidence = 0.7f
                )
            }
        }
        
        // Check personal info patterns
        for (pattern in personalInfoPatterns) {
            val match = pattern.find(trimmed)
            if (match != null && trimmed.length < 100) {
                return MemoryDetectionResult(
                    content = trimmed,
                    category = MemoryCategory.PERSONAL,
                    confidence = 0.6f
                )
            }
        }
        
        return null
    }
    
    /**
     * Try to categorize content based on keywords
     */
    private fun categorizeContent(content: String): MemoryCategory {
        val lower = content.lowercase()
        
        return when {
            // Work-related
            lower.contains("werk") || lower.contains("work") ||
            lower.contains("meeting") || lower.contains("vergadering") ||
            lower.contains("project") || lower.contains("deadline") -> MemoryCategory.WORK
            
            // Location
            lower.contains("adres") || lower.contains("address") ||
            lower.contains("parkeer") || lower.contains("parking") ||
            lower.contains("locatie") || lower.contains("location") -> MemoryCategory.LOCATION
            
            // Contact
            lower.contains("telefoon") || lower.contains("phone") ||
            lower.contains("email") || lower.contains("nummer") ||
            lower.contains("contact") -> MemoryCategory.CONTACT
            
            // Todo
            lower.contains("moet") || lower.contains("need to") ||
            lower.contains("todo") || lower.contains("te doen") -> MemoryCategory.TODO
            
            // Personal
            lower.contains("favoriet") || lower.contains("favorite") ||
            lower.contains("verjaardag") || lower.contains("birthday") ||
            lower.contains("allergisch") || lower.contains("allergic") -> MemoryCategory.PERSONAL
            
            else -> MemoryCategory.GENERAL
        }
    }
}

data class MemoryDetectionResult(
    val content: String,
    val category: MemoryCategory,
    val confidence: Float // 0.0 to 1.0
)
