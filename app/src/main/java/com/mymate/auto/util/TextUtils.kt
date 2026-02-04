package com.mymate.auto.util

/**
 * Utility functions for text processing
 */
object TextUtils {
    
    /**
     * Strip markdown formatting from text for clean display
     */
    fun stripMarkdown(text: String): String {
        var result = text
        
        // Remove bold **text** and __text__
        result = result.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        result = result.replace(Regex("__(.+?)__"), "$1")
        
        // Remove italic *text* and _text_ (but not in the middle of words)
        result = result.replace(Regex("(?<![\\w*])\\*([^*]+?)\\*(?![\\w*])"), "$1")
        result = result.replace(Regex("(?<![\\w_])_([^_]+?)_(?![\\w_])"), "$1")
        
        // Remove strikethrough ~~text~~
        result = result.replace(Regex("~~(.+?)~~"), "$1")
        
        // Remove inline code `text`
        result = result.replace(Regex("`([^`]+?)`"), "$1")
        
        // Remove code blocks ```text```
        result = result.replace(Regex("```[\\s\\S]*?```"), "")
        
        // Remove headers # ## ### etc
        result = result.replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        
        // Remove blockquotes >
        result = result.replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
        
        // Remove horizontal rules --- or ***
        result = result.replace(Regex("^[-*]{3,}$", RegexOption.MULTILINE), "")
        
        // Remove link syntax [text](url) -> text
        result = result.replace(Regex("\\[([^\\]]+?)\\]\\([^)]+?\\)"), "$1")
        
        // Remove image syntax ![alt](url)
        result = result.replace(Regex("!\\[([^\\]]*?)\\]\\([^)]+?\\)"), "$1")
        
        // Remove bullet points - or * at start of line
        result = result.replace(Regex("^[\\-*]\\s+", RegexOption.MULTILINE), "• ")
        
        // Remove numbered lists 1. 2. etc
        result = result.replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
        
        // Clean up multiple newlines
        result = result.replace(Regex("\n{3,}"), "\n\n")
        
        // Trim whitespace
        result = result.trim()
        
        return result
    }
    
    /**
     * Prepare text for TTS - remove emojis and special characters
     */
    fun prepareForTts(text: String): String {
        var result = stripMarkdown(text)
        
        // Remove emojis (basic emoji ranges)
        result = result.replace(Regex("[\\x{1F600}-\\x{1F64F}]"), "") // Emoticons
        result = result.replace(Regex("[\\x{1F300}-\\x{1F5FF}]"), "") // Misc Symbols
        result = result.replace(Regex("[\\x{1F680}-\\x{1F6FF}]"), "") // Transport
        result = result.replace(Regex("[\\x{1F1E0}-\\x{1F1FF}]"), "") // Flags
        result = result.replace(Regex("[\\x{2600}-\\x{26FF}]"), "")   // Misc symbols
        result = result.replace(Regex("[\\x{2700}-\\x{27BF}]"), "")   // Dingbats
        
        // Remove URLs
        result = result.replace(Regex("https?://\\S+"), "")
        
        // Clean up whitespace
        result = result.replace(Regex("\\s+"), " ").trim()
        
        return result
    }
}
