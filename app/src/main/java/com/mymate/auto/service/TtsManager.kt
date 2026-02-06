package com.mymate.auto.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.mymate.auto.util.TextUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID

/**
 * Text-to-Speech manager for reading responses aloud
 * 
 * Handles audio focus for Android Auto compatibility.
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    
    companion object {
        private const val TAG = "TtsManager"
        
        @Volatile
        private var instance: TtsManager? = null
        
        fun getInstance(context: Context): TtsManager {
            return instance ?: synchronized(this) {
                instance ?: TtsManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private val audioFocusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { /* ignore focus changes */ }
            .build()
    } else null
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady
    
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking
    
    private var initialized = false
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Try Dutch first, fallback to default
            val dutchResult = tts.setLanguage(Locale("nl", "NL"))
            if (dutchResult == TextToSpeech.LANG_MISSING_DATA || 
                dutchResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to device default
                tts.setLanguage(Locale.getDefault())
                Log.w(TAG, "Dutch TTS not available, using default locale")
            } else {
                Log.d(TAG, "TTS initialized with Dutch")
            }
            
            // Set speech rate (1.0 = normal, 0.5 = slow, 1.5 = fast)
            tts.setSpeechRate(1.0f)
            
            // Set pitch (1.0 = normal)
            tts.setPitch(1.0f)
            
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }
                
                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    abandonAudioFocus()
                }
                
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    abandonAudioFocus()
                    Log.e(TAG, "TTS error for utterance: $utteranceId")
                }
                
                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    abandonAudioFocus()
                    Log.e(TAG, "TTS error $errorCode for utterance: $utteranceId")
                }
            })
            
            initialized = true
            _isReady.value = true
            Log.d(TAG, "TTS initialized successfully")
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
            _isReady.value = false
        }
    }
    
    /**
     * Speak the given text
     * @param text Text to speak
     * @param stripMarkdown Whether to strip markdown formatting first
     */
    fun speak(text: String, stripMarkdown: Boolean = true) {
        if (!initialized) {
            Log.w(TAG, "TTS not initialized yet, retrying in 500ms")
            // Retry after short delay - TTS may still be initializing
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (initialized) {
                    speak(text, stripMarkdown)
                } else {
                    Log.e(TAG, "TTS still not initialized after retry")
                }
            }, 500)
            return
        }
        
        val textToSpeak = if (stripMarkdown) {
            TextUtils.prepareForTts(text)
        } else {
            text
        }
        
        if (textToSpeak.isBlank()) {
            Log.d(TAG, "Nothing to speak (text is blank after processing)")
            return
        }
        
        val utteranceId = UUID.randomUUID().toString()
        
        Log.d(TAG, "Speaking: ${textToSpeak.take(50)}...")
        
        // Request audio focus for Android Auto compatibility
        requestAudioFocus()
        
        tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
    
    @Suppress("DEPRECATION")
    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.requestAudioFocus(audioFocusRequest)
            } else {
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
            Log.d(TAG, "Audio focus requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request audio focus: ${e.message}")
        }
    }
    
    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to abandon audio focus: ${e.message}")
        }
    
    /**
     * Add text to the speech queue
     */
    fun speakQueued(text: String, stripMarkdown: Boolean = true) {
        if (!initialized) return
        
        val textToSpeak = if (stripMarkdown) {
            TextUtils.prepareForTts(text)
        } else {
            text
        }
        
        if (textToSpeak.isBlank()) return
        
        val utteranceId = UUID.randomUUID().toString()
        tts.speak(textToSpeak, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }
    
    /**
     * Stop speaking
     */
    fun stop() {
        if (initialized) {
            tts.stop()
            _isSpeaking.value = false
        }
    }
    
    /**
     * Check if TTS is currently speaking
     */
    fun isSpeakingNow(): Boolean = tts.isSpeaking
    
    /**
     * Cleanup TTS resources
     */
    fun shutdown() {
        tts.stop()
        tts.shutdown()
        _isReady.value = false
        _isSpeaking.value = false
        instance = null
    }
}
