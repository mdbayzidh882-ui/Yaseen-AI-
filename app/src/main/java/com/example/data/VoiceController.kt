package com.example.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.model.SathiVoiceProfile
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceEngineState(
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val partialTranscript: String = "",
    val ttsReady: Boolean = false,
    val speechErrorMessage: String? = null
)

class VoiceController(
    context: Context,
    private val onFinalTranscript: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(VoiceEngineState())
    val state: StateFlow<VoiceEngineState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var activeLanguage: SathiLanguage = SathiLanguage.BN

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = _state.value.copy(speaking = true)
                    }

                    override fun onDone(utteranceId: String?) {
                        _state.value = _state.value.copy(speaking = false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(speaking = false)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _state.value = _state.value.copy(speaking = false)
                    }
                })
                _state.value = _state.value.copy(ttsReady = true)
            } else {
                Log.w(TAG, "TextToSpeech initialization returned status=$status")
            }
        }
    }

    fun startListening(language: SathiLanguage) {
        activeLanguage = language
        mainHandler.post {
            stopSpeakingInternal()
            _state.value = _state.value.copy(speechErrorMessage = null, partialTranscript = "")

            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _state.value = _state.value.copy(
                    listening = false,
                    speechErrorMessage = if (language == SathiLanguage.BN) {
                        "এই ডিভাইসে ভয়েস রেকগনিশন পাওয়া যায়নি। নিচে লিখে কথা বলতে পারেন।"
                    } else {
                        "Speech recognition is not available on this device. You can type below."
                    }
                )
                return@post
            }

            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _state.value = _state.value.copy(listening = true, speechErrorMessage = null)
                        }

                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            _state.value = _state.value.copy(listening = false)
                        }

                        override fun onError(error: Int) {
                            val msg = mapSpeechError(error, activeLanguage)
                            _state.value = _state.value.copy(
                                listening = false,
                                speechErrorMessage = msg
                            )
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val spoken = matches?.firstOrNull()?.trim().orEmpty()
                            _state.value = _state.value.copy(
                                listening = false,
                                partialTranscript = spoken
                            )
                            if (spoken.isNotEmpty()) {
                                onFinalTranscript(spoken)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                            if (partial.isNotBlank()) {
                                _state.value = _state.value.copy(partialTranscript = partial)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val localeTag = if (language == SathiLanguage.BN) "bn-BD" else "en-US"
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
                _state.value = _state.value.copy(listening = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start listening", e)
                _state.value = _state.value.copy(
                    listening = false,
                    speechErrorMessage = if (language == SathiLanguage.BN) {
                        "মাইক্রোফোন চালু করতে সমস্যা হয়েছে। অনুগ্রহ করে আবার চেষ্টা করুন।"
                    } else {
                        "Could not start microphone. Please try again."
                    }
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
            _state.value = _state.value.copy(listening = false)
        }
    }

    fun interruptAndListen(language: SathiLanguage) {
        stopSpeaking()
        startListening(language)
    }

    fun speak(
        text: String,
        language: SathiLanguage,
        voiceProfile: SathiVoiceProfile,
        mood: SathiMood
    ) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val engine = tts ?: return

        mainHandler.post {
            try {
                val targetLocale = if (language == SathiLanguage.BN ||
                    cleanText.any { it in '\u0980'..'\u09FF' }
                ) {
                    Locale("bn", "BD")
                } else {
                    Locale.US
                }

                val langResult = engine.setLanguage(targetLocale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine.setLanguage(Locale.US)
                }

                selectSyntheticVoice(engine, targetLocale, voiceProfile)

                val basePitch = if (voiceProfile == SathiVoiceProfile.FEMALE) 1.10f else 0.85f
                val moodPitchDelta = when (mood) {
                    SathiMood.HAPPY, SathiMood.PLAYFUL -> 0.06f
                    SathiMood.COMFORTING -> -0.04f
                    SathiMood.SERIOUS -> -0.03f
                    SathiMood.WARM -> 0.0f
                }
                val moodRate = when (mood) {
                    SathiMood.HAPPY, SathiMood.PLAYFUL -> 1.03f
                    SathiMood.COMFORTING -> 0.88f
                    SathiMood.SERIOUS -> 0.94f
                    SathiMood.WARM -> 0.97f
                }

                engine.setPitch((basePitch + moodPitchDelta).coerceIn(0.65f, 1.45f))
                engine.setSpeechRate(moodRate)

                val utteranceId = UUID.randomUUID().toString()
                _state.value = _state.value.copy(speaking = true)
                engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } catch (e: Exception) {
                Log.e(TAG, "TTS speak failed", e)
                _state.value = _state.value.copy(speaking = false)
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post { stopSpeakingInternal() }
    }

    private fun stopSpeakingInternal() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(speaking = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(speechErrorMessage = null)
    }

    fun release() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                tts?.stop()
                tts?.shutdown()
                tts = null
            } catch (_: Exception) {
            }
        }
    }

    private fun selectSyntheticVoice(
        engine: TextToSpeech,
        locale: Locale,
        profile: SathiVoiceProfile
    ) {
        try {
            val voices = engine.voices ?: return
            val matchingLocale = voices.filter { v ->
                v.locale.language.equals(locale.language, ignoreCase = true)
            }
            if (matchingLocale.isEmpty()) return

            val preferred: Voice? = if (profile == SathiVoiceProfile.MALE) {
                matchingLocale.firstOrNull { v ->
                    val n = v.name.lowercase()
                    n.contains("male") || n.contains("-b-") || n.contains("-d-") || n.contains("rishi")
                }
            } else {
                matchingLocale.firstOrNull { v ->
                    val n = v.name.lowercase()
                    n.contains("female") || n.contains("-a-") || n.contains("-c-") || n.contains("kalpana")
                }
            }
            (preferred ?: matchingLocale.firstOrNull())?.let { engine.voice = it }
        } catch (_: Exception) {
        }
    }

    private fun mapSpeechError(error: Int, language: SathiLanguage): String? {
        return when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                if (language == SathiLanguage.BN) "আপনার কথা স্পষ্ট শুনতে পাইনি, অনুগ্রহ করে আবার বলুন।"
                else "I didn't catch that clearly. Please try speaking again."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                if (language == SathiLanguage.BN) "ভয়েস চিনতে নেটওয়ার্ক সমস্যা হয়েছে, নিচে লিখে কথা বলতে পারেন।"
                else "Network issue while listening. You can type your message below."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                if (language == SathiLanguage.BN) "কথা শোনার জন্য মাইক্রোফোন অনুমতি প্রয়োজন।"
                else "Microphone permission is needed to listen."
            SpeechRecognizer.ERROR_CLIENT -> null
            else ->
                if (language == SathiLanguage.BN) "মাইক্রোফোনে সাময়িক সমস্যা হয়েছে, আবার চেষ্টা করুন।"
                else "Microphone had a brief issue. Please tap again."
        }
    }

    companion object {
        private const val TAG = "VoiceController"
    }
}
