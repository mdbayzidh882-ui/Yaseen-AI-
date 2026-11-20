package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LocalSettingsState
import com.example.core.NetworkMonitor
import com.example.core.OfflinePackState
import com.example.core.OfflinePackTier
import com.example.core.SathiPreferences
import com.example.data.ActionOutcome
import com.example.data.DeviceActionExecutor
import com.example.data.OfflineCompanionEngine
import com.example.data.SathiAiService
import com.example.data.SathiFirestoreRepository
import com.example.data.VoiceController
import com.example.data.VoiceEngineState
import com.example.data.WebSearchService
import com.example.model.ActivityLog
import com.example.model.AppConfig
import com.example.model.ChatMessage
import com.example.model.CodeType
import com.example.model.MemoryCategory
import com.example.model.PendingConfirmation
import com.example.model.PhoneActionType
import com.example.model.PlanTier
import com.example.model.PremiumCode
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.model.SathiVoiceProfile
import com.example.model.UserMemory
import com.example.model.UserProfile
import com.example.model.WebSource
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiBannerState {
    data object None : UiBannerState
    data class Info(val message: String) : UiBannerState
    data class Error(val message: String) : UiBannerState
}

class SathiViewModel(
    private val currentUserId: String,
    private val userEmail: String,
    private val displayName: String,
    private val repository: SathiFirestoreRepository,
    private val aiService: SathiAiService,
    private val webSearchService: WebSearchService,
    private val offlineEngine: OfflineCompanionEngine,
    private val voiceController: VoiceController,
    private val deviceActions: DeviceActionExecutor,
    private val preferences: SathiPreferences,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    val voiceState: StateFlow<VoiceEngineState> = voiceController.state

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Profile stream error", fsErr ?: error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val memories: StateFlow<List<UserMemory>> = repository.observeMemories(currentUserId)
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Memories stream error", fsErr ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val globalConfig: StateFlow<AppConfig> = repository.observeGlobalConfig()
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(AppConfig())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppConfig())

    val localSettings: StateFlow<LocalSettingsState> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), LocalSettingsState())

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _activeMood = MutableStateFlow(SathiMood.WARM)
    val activeMood: StateFlow<SathiMood> = _activeMood.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _bannerState = MutableStateFlow<UiBannerState>(UiBannerState.None)
    val bannerState: StateFlow<UiBannerState> = _bannerState.asStateFlow()

    private val _studioLoading = MutableStateFlow(false)
    val studioLoading: StateFlow<Boolean> = _studioLoading.asStateFlow()

    private val _studioResult = MutableStateFlow("")
    val studioResult: StateFlow<String> = _studioResult.asStateFlow()

    private val _morningGreeting = MutableStateFlow("")
    val morningGreeting: StateFlow<String> = _morningGreeting.asStateFlow()

    private val _isOwnerAdmin = MutableStateFlow(false)
    val isOwnerAdmin: StateFlow<Boolean> = _isOwnerAdmin.asStateFlow()

    private val _adminUnlocked = MutableStateFlow(false)
    val adminUnlocked: StateFlow<Boolean> = _adminUnlocked.asStateFlow()

    private val _adminCodes = MutableStateFlow<List<PremiumCode>>(emptyList())
    val adminCodes: StateFlow<List<PremiumCode>> = _adminCodes.asStateFlow()

    private val _adminUsers = MutableStateFlow<List<UserProfile>>(emptyList())
    val adminUsers: StateFlow<List<UserProfile>> = _adminUsers.asStateFlow()

    private val _adminLogs = MutableStateFlow<List<ActivityLog>>(emptyList())
    val adminLogs: StateFlow<List<ActivityLog>> = _adminLogs.asStateFlow()

    private var offlineDownloadJob: Job? = null
    private var adminObserverJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId, displayName, userEmail)
            _isOwnerAdmin.value = repository.isUserAdminDocPresent(currentUserId)
            seedWelcomeMessage()
        }
    }

    private fun seedWelcomeMessage() {
        if (_messages.value.isNotEmpty()) return
        val name = displayName.ifBlank { "বন্ধু" }
        _messages.value = listOf(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                isUser = false,
                text = "আসসালামু আলাইকুম $name! আমি সাথী, আপনার ব্যক্তিগত এআই বন্ধু। নিচের মাইক্রোফোন বাটনে ট্যাপ করে বাংলা বা ইংরেজিতে কথা বলুন।",
                mood = SathiMood.WARM
            )
        )
    }

    fun currentLanguage(): SathiLanguage =
        SathiLanguage.fromCode(
            userProfile.value?.preferredLanguage ?: localSettings.value.preferredLanguageFallback
        )

    fun currentVoice(): SathiVoiceProfile =
        SathiVoiceProfile.fromCode(
            userProfile.value?.preferredVoice ?: localSettings.value.preferredVoiceFallback
        )

    fun isUsingOfflineMode(): Boolean {
        val settings = localSettings.value
        val online = networkMonitor.isOnline()
        return settings.offlineModeEnabled || (!online && settings.offlinePackState == OfflinePackState.INSTALLED)
    }

    fun onMicButtonTapped() {
        val vs = voiceState.value
        val lang = currentLanguage()
        when {
            vs.speaking -> {
                // Barge-in / Interruption
                voiceController.interruptAndListen(lang)
            }
            vs.listening -> {
                voiceController.stopListening()
            }
            else -> {
                voiceController.startListening(lang)
            }
        }
    }

    fun sendUserMessage(rawText: String, speakReply: Boolean = true) {
        val text = rawText.trim()
        if (text.isEmpty() || _isThinking.value) return

        val profile = userProfile.value
        if (profile?.banned == true) {
            val lang = currentLanguage()
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "দুঃখিত, আপনার অ্যাকাউন্টটি অ্যাডমিন দ্বারা স্থগিত করা হয়েছে।"
                else "Your account has been suspended by the administrator."
            )
            return
        }

        val lang = currentLanguage()
        val voice = currentVoice()
        val userName = profile?.userName?.ifBlank { displayName } ?: displayName.ifBlank { "বন্ধু" }

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = text
        )
        _messages.value = _messages.value + userMsg
        _isThinking.value = true
        _bannerState.value = UiBannerState.None

        viewModelScope.launch {
            try {
                val settings = localSettings.value
                val online = networkMonitor.isOnline()

                if (settings.offlineModeEnabled || !online) {
                    if (settings.offlinePackState == OfflinePackState.INSTALLED) {
                        val offlineTurn = offlineEngine.respondOffline(text, lang, userName)
                        handleTurnOutput(offlineTurn, emptyList(), isOffline = true, speakReply = speakReply, lang = lang, voice = voice)
                    } else if (!online) {
                        val msg = if (lang == SathiLanguage.BN) {
                            "ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না। অফলাইন চ্যাট ব্যবহার করতে সেটিংস থেকে অফলাইন প্যাক ডাউনলোড করুন অথবা ওয়াই-ফাই/ডাটা চালু করুন।"
                        } else {
                            "No internet connection. Enable Wi-Fi/data or download the Offline Pack in Settings."
                        }
                        appendSathiErrorReply(msg, lang, voice, speakReply)
                    } else {
                        // Online but offline mode toggle requested without pack
                        val offlineTurn = offlineEngine.respondOffline(text, lang, userName)
                        handleTurnOutput(offlineTurn, emptyList(), isOffline = true, speakReply = speakReply, lang = lang, voice = voice)
                    }
                    return@launch
                }

                // Online Cloud AI via Firebase AI Logic
                val allowMemory = (profile?.hasPremiumAccess() == true) || globalConfig.value.longTermMemoryForFree
                val recentHistory = _messages.value.takeLast(7).map { it.isUser to it.text }

                val initialResult = aiService.generateCompanionReply(
                    userMessage = text,
                    language = lang,
                    userName = userName,
                    memories = memories.value,
                    recentHistory = recentHistory,
                    webSources = emptyList(),
                    allowLongTermMemory = allowMemory
                )

                val turn = initialResult.getOrElse { err ->
                    val friendly = mapFriendlyAiError(err, lang)
                    appendSathiErrorReply(friendly, lang, voice, speakReply)
                    return@launch
                }

                // If AI requests live web search, fetch real HTTPS sources and summarize
                if (turn.actionType == PhoneActionType.WEB_SEARCH) {
                    val searchQuery = turn.actionParam.ifBlank { text }
                    val sources = webSearchService.searchWeb(searchQuery, lang)
                    if (sources.isNotEmpty()) {
                        val groundedResult = aiService.generateCompanionReply(
                            userMessage = text,
                            language = lang,
                            userName = userName,
                            memories = memories.value,
                            recentHistory = recentHistory,
                            webSources = sources,
                            allowLongTermMemory = allowMemory
                        ).getOrElse { turn }
                        handleTurnOutput(groundedResult, sources, isOffline = false, speakReply = speakReply, lang = lang, voice = voice)
                    } else {
                        handleTurnOutput(turn, emptyList(), isOffline = false, speakReply = speakReply, lang = lang, voice = voice)
                    }
                } else {
                    handleTurnOutput(turn, emptyList(), isOffline = false, speakReply = speakReply, lang = lang, voice = voice)
                }
            } finally {
                _isThinking.value = false
            }
        }
    }

    private suspend fun handleTurnOutput(
        turn: com.example.data.SathiTurnResult,
        sources: List<WebSource>,
        isOffline: Boolean,
        speakReply: Boolean,
        lang: SathiLanguage,
        voice: SathiVoiceProfile
    ) {
        _activeMood.value = turn.mood

        // Save memory if premium/enabled and detected
        val canSaveMemory = (userProfile.value?.hasPremiumAccess() == true) || globalConfig.value.longTermMemoryForFree
        if (turn.shouldSaveMemory && canSaveMemory && turn.memoryContent.isNotBlank()) {
            repository.addMemory(
                uid = currentUserId,
                category = turn.memoryCategory,
                title = turn.memoryTitle.ifBlank { "মনে রাখা তথ্য" },
                content = turn.memoryContent
            )
        }

        // Execute phone action if requested
        var actionLabel: String? = null
        if (turn.actionType != PhoneActionType.NONE && turn.actionType != PhoneActionType.WEB_SEARCH) {
            when (val outcome = deviceActions.executeAction(turn.actionType, turn.actionParam, turn.secondaryParam, lang)) {
                is ActionOutcome.Executed -> {
                    if (outcome.label.isNotBlank()) actionLabel = outcome.label
                }
                is ActionOutcome.NeedsConfirmation -> {
                    _pendingConfirmation.value = outcome.confirmation
                }
                is ActionOutcome.Failed -> {
                    _bannerState.value = UiBannerState.Error(outcome.message)
                }
            }
        }

        val replyMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = turn.replyText,
            mood = turn.mood,
            sources = sources,
            actionLabel = actionLabel,
            isOfflineReply = isOffline
        )
        _messages.value = _messages.value + replyMsg
        if (speakReply) {
            voiceController.speak(turn.replyText, lang, voice, turn.mood)
        }
    }

    private fun appendSathiErrorReply(
        message: String,
        lang: SathiLanguage,
        voice: SathiVoiceProfile,
        speakReply: Boolean
    ) {
        _messages.value = _messages.value + ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = message,
            mood = SathiMood.COMFORTING
        )
        _bannerState.value = UiBannerState.Error(message)
        if (speakReply) {
            voiceController.speak(message, lang, voice, SathiMood.COMFORTING)
        }
    }

    fun confirmPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        val lang = currentLanguage()
        val outcome = when (pending.type) {
            PhoneActionType.CALL_CONFIRM -> deviceActions.executeConfirmedCall(pending.target, lang)
            PhoneActionType.SMS_CONFIRM -> deviceActions.executeConfirmedSms(pending.target, pending.messageBody, lang)
            else -> ActionOutcome.Executed("")
        }
        when (outcome) {
            is ActionOutcome.Executed -> if (outcome.label.isNotBlank()) {
                _bannerState.value = UiBannerState.Info(outcome.label)
            }
            is ActionOutcome.Failed -> _bannerState.value = UiBannerState.Error(outcome.message)
            is ActionOutcome.NeedsConfirmation -> {}
        }
    }

    fun dismissPendingAction() {
        _pendingConfirmation.value = null
    }

    fun speakMessage(text: String, mood: SathiMood = SathiMood.WARM) {
        voiceController.speak(text, currentLanguage(), currentVoice(), mood)
    }

    fun previewVoiceSample(profile: SathiVoiceProfile) {
        val lang = currentLanguage()
        val sample = if (lang == SathiLanguage.BN) {
            if (profile == SathiVoiceProfile.FEMALE) {
                "নমস্কার! আমি সাথী, আপনার মিষ্টি মেয়ে কণ্ঠের সাথী। কেমন আছেন আজ?"
            } else {
                "সালাম! আমি সাথী, আপনার মার্জিত ছেলে কণ্ঠের সাথী। আজ আপনাকে কীভাবে সাহায্য করতে পারি?"
            }
        } else {
            if (profile == SathiVoiceProfile.FEMALE) {
                "Hi! I am Sathi in the sweet female voice. I am always here with you."
            } else {
                "Hello! I am Sathi in the warm male voice. How can I help you today?"
            }
        }
        voiceController.speak(sample, lang, profile, SathiMood.WARM)
    }

    fun updateLanguageAndVoice(language: SathiLanguage, voice: SathiVoiceProfile, userName: String? = null) {
        viewModelScope.launch {
            preferences.saveLanguageAndVoice(language.code, voice.code)
            val resolvedName = userName ?: userProfile.value?.userName ?: displayName
            repository.updateUserPreferences(currentUserId, resolvedName, language.code, voice.code)
        }
    }

    // --- Memory CRUD ---

    fun addMemory(category: MemoryCategory, title: String, content: String) {
        viewModelScope.launch {
            val res = repository.addMemory(currentUserId, category, title, content)
            if (res.isFailure) {
                _bannerState.value = UiBannerState.Error(
                    if (currentLanguage() == SathiLanguage.BN) "স্মৃতি সংরক্ষণ করা যায়নি।"
                    else "Could not save memory."
                )
            }
        }
    }

    fun updateMemory(memoryId: String, category: MemoryCategory, title: String, content: String) {
        viewModelScope.launch {
            val res = repository.updateMemory(currentUserId, memoryId, category, title, content)
            if (res.isFailure) {
                _bannerState.value = UiBannerState.Error(
                    if (currentLanguage() == SathiLanguage.BN) "স্মৃতি হালনাগাদ করা যায়নি।"
                    else "Could not update memory."
                )
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(currentUserId, memoryId)
        }
    }

    fun deleteAllUserData() {
        viewModelScope.launch {
            repository.deleteAllUserData(currentUserId)
            preferences.clearAll()
            _messages.value = emptyList()
            seedWelcomeMessage()
            val lang = currentLanguage()
            _bannerState.value = UiBannerState.Info(
                if (lang == SathiLanguage.BN) "আপনার সব স্মৃতি ও ডেটা মুছে ফেলা হয়েছে।"
                else "All your memories and local data have been deleted."
            )
        }
    }

    // --- Premium Studio Tools ---

    fun generateMorningGreeting() {
        val lang = currentLanguage()
        if (!networkMonitor.isOnline()) {
            _morningGreeting.value = if (lang == SathiLanguage.BN) {
                "শুভ সকাল! নতুন দিনের আলোয় আপনার মন থাকুক প্রশান্ত ও আনন্দময়।"
            } else {
                "Good morning! May today bring peace, clarity, and gentle joy."
            }
            return
        }
        viewModelScope.launch {
            _studioLoading.value = true
            val memorySummary = memories.value.take(3).joinToString(", ") { "${it.title}: ${it.content}" }
            val prompt = "Write a short, uplifting, heartfelt 2-sentence morning greeting for ${userProfile.value?.userName ?: displayName}."
            val res = aiService.runStudioTool(prompt, memorySummary.ifBlank { "New fresh day" }, lang)
            _studioLoading.value = false
            res.onSuccess {
                _morningGreeting.value = it
                voiceController.speak(it, lang, currentVoice(), SathiMood.HAPPY)
            }.onFailure {
                _bannerState.value = UiBannerState.Error(mapFriendlyAiError(it, lang))
            }
        }
    }

    fun analyzeCameraBitmap(bitmap: Bitmap, question: String) {
        val lang = currentLanguage()
        if (!networkMonitor.isOnline()) {
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "ক্যামেরা বিশ্লেষণের জন্য ইন্টারনেট সংযোগ প্রয়োজন।"
                else "Internet connection is required for camera understanding."
            )
            return
        }
        viewModelScope.launch {
            _studioLoading.value = true
            val res = aiService.analyzeImage(bitmap, question, lang)
            _studioLoading.value = false
            res.onSuccess {
                _studioResult.value = it
                voiceController.speak(it, lang, currentVoice(), SathiMood.WARM)
            }.onFailure {
                _bannerState.value = UiBannerState.Error(mapFriendlyAiError(it, lang))
            }
        }
    }

    fun runPremiumStudioTool(instruction: String, input: String) {
        val lang = currentLanguage()
        if (!networkMonitor.isOnline()) {
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "এই এআই টুলটির জন্য ইন্টারনেট প্রয়োজন।"
                else "Internet connection is required for this AI tool."
            )
            return
        }
        viewModelScope.launch {
            _studioLoading.value = true
            val res = aiService.runStudioTool(instruction, input, lang)
            _studioLoading.value = false
            res.onSuccess {
                _studioResult.value = it
            }.onFailure {
                _bannerState.value = UiBannerState.Error(mapFriendlyAiError(it, lang))
            }
        }
    }

    // --- Plans & Code Redemption ---

    fun redeemCode(rawCode: String) {
        val lang = currentLanguage()
        val now = System.currentTimeMillis()
        val lockoutUntil = localSettings.value.codeLockoutUntilMs
        if (now < lockoutUntil) {
            val minsLeft = ((lockoutUntil - now) / 60_000L) + 1
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "অনেকবার ভুল কোড দিয়েছেন। অনুগ্রহ করে $minsLeft মিনিট পর চেষ্টা করুন।"
                else "Too many wrong attempts. Please try again in $minsLeft minute(s)."
            )
            return
        }

        viewModelScope.launch {
            val res = repository.redeemPremiumCode(currentUserId, rawCode)
            res.onSuccess { planTier ->
                preferences.resetCodeFailures()
                _bannerState.value = UiBannerState.Info(
                    if (lang == SathiLanguage.BN) "অভিনন্দন! আপনার প্ল্যান এখন: ${planTier.labelBn}"
                    else "Success! Your plan is now: ${planTier.labelEn}"
                )
            }.onFailure {
                val lockedUntil = preferences.recordCodeFailure()
                val msg = if (lockedUntil > 0L) {
                    if (lang == SathiLanguage.BN) "ভুল কোড! নিরাপত্তার জন্য ৫ মিনিটের জন্য কোড যাচাই স্থগিত করা হয়েছে।"
                    else "Invalid code. Verification locked for 5 minutes for security."
                } else {
                    if (lang == SathiLanguage.BN) "কোডটি সঠিক নয় বা ইতিমধ্যে ব্যবহৃত/বাতিল হয়েছে।"
                    else "That code is invalid, already used, or revoked."
                }
                _bannerState.value = UiBannerState.Error(msg)
            }
        }
    }

    // --- Offline Pack Manager ---

    fun selectOfflinePackTier(tier: OfflinePackTier) {
        viewModelScope.launch { preferences.setOfflinePackTier(tier) }
    }

    fun setWifiOnlyDownload(wifiOnly: Boolean) {
        viewModelScope.launch { preferences.setWifiOnlyDownload(wifiOnly) }
    }

    fun setOfflineModeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setOfflineModeEnabled(enabled) }
    }

    fun startOrResumeOfflineDownload() {
        val lang = currentLanguage()
        val settings = localSettings.value
        if (!networkMonitor.isOnline()) {
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "অফলাইন প্যাক ডাউনলোড করতে ইন্টারনেট প্রয়োজন।"
                else "Internet connection is needed to download the Offline Pack."
            )
            return
        }
        if (settings.wifiOnlyDownload && !networkMonitor.isWifiConnected()) {
            _bannerState.value = UiBannerState.Error(
                if (lang == SathiLanguage.BN) "ওয়াই-ফাই ছাড়া ডাউনলোড বন্ধ আছে। ওয়াই-ফাই চালু করুন অথবা সেটিংস থেকে 'শুধু ওয়াই-ফাই' অপশনটি বন্ধ করুন।"
                else "Wi-Fi Only is enabled. Connect to Wi-Fi or turn off Wi-Fi Only to download."
            )
            return
        }

        offlineDownloadJob?.cancel()
        offlineDownloadJob = viewModelScope.launch {
            var progress = settings.offlineDownloadProgress.coerceIn(0f, 0.99f)
            preferences.setOfflinePackState(OfflinePackState.DOWNLOADING, progress)
            while (progress < 1f) {
                delay(180L)
                progress = (progress + 0.04f).coerceAtMost(1f)
                preferences.setOfflinePackState(
                    if (progress >= 1f) OfflinePackState.INSTALLED else OfflinePackState.DOWNLOADING,
                    progress
                )
            }
            _bannerState.value = UiBannerState.Info(
                if (lang == SathiLanguage.BN) "অফলাইন প্যাক ডাউনলোড সম্পন্ন হয়েছে! এখন ইন্টারনেট ছাড়াও সাথীর সাথে কথা বলা যাবে।"
                else "Offline Pack downloaded! You can now chat with Sathi offline."
            )
        }
    }

    fun pauseOfflineDownload() {
        offlineDownloadJob?.cancel()
        viewModelScope.launch {
            preferences.setOfflinePackState(
                OfflinePackState.PAUSED,
                localSettings.value.offlineDownloadProgress
            )
        }
    }

    fun deleteOfflinePack() {
        offlineDownloadJob?.cancel()
        viewModelScope.launch {
            preferences.setOfflinePackState(OfflinePackState.NOT_INSTALLED, 0f)
            preferences.setOfflineModeEnabled(false)
        }
    }

    // --- Admin Panel ---

    fun unlockAdminSession() {
        if (!_isOwnerAdmin.value) return
        _adminUnlocked.value = true
        adminObserverJob?.cancel()
        adminObserverJob = viewModelScope.launch {
            launch {
                repository.observeAllCodes()
                    .catch { emit(emptyList()) }
                    .collect { _adminCodes.value = it }
            }
            launch {
                repository.observeAllUsers()
                    .catch { emit(emptyList()) }
                    .collect { _adminUsers.value = it }
            }
            launch {
                repository.observeActivityLogs()
                    .catch { emit(emptyList()) }
                    .collect { _adminLogs.value = it }
            }
        }
    }

    fun lockAdminSession() {
        _adminUnlocked.value = false
        adminObserverJob?.cancel()
    }

    fun adminGenerateCode(type: CodeType, note: String) {
        viewModelScope.launch {
            val res = repository.generatePremiumCode(currentUserId, userEmail, type, note)
            res.onSuccess { code ->
                _bannerState.value = UiBannerState.Info("Generated Code: $code")
            }.onFailure {
                _bannerState.value = UiBannerState.Error("Failed to generate code.")
            }
        }
    }

    fun adminRevokeCode(codeId: String) {
        viewModelScope.launch {
            repository.revokePremiumCode(currentUserId, userEmail, codeId)
        }
    }

    fun adminUpdateUser(targetUid: String, plan: PlanTier, banned: Boolean) {
        viewModelScope.launch {
            repository.adminUpdateUser(currentUserId, userEmail, targetUid, plan, banned)
        }
    }

    fun adminSaveGlobalConfig(config: AppConfig) {
        viewModelScope.launch {
            val res = repository.adminUpdateGlobalConfig(currentUserId, userEmail, config)
            if (res.isSuccess) {
                _bannerState.value = UiBannerState.Info(
                    if (currentLanguage() == SathiLanguage.BN) "গ্লোবাল সেটিংস সংরক্ষিত হয়েছে।"
                    else "Global settings saved."
                )
            }
        }
    }

    fun clearBanner() {
        _bannerState.value = UiBannerState.None
        voiceController.clearError()
    }

    private fun mapFriendlyAiError(e: Throwable, language: SathiLanguage): String {
        val msg = e.localizedMessage.orEmpty().lowercase()
        return when {
            msg.contains("timeout") ->
                if (language == SathiLanguage.BN) "নেটওয়ার্ক ধীরগতির হওয়ায় উত্তর আসতে দেরি হচ্ছে। আবার চেষ্টা করুন।"
                else "The connection is slow and timed out. Please try again."
            msg.contains("network") || msg.contains("unavailable") || !networkMonitor.isOnline() ->
                if (language == SathiLanguage.BN) "ইন্টারনেট সংযোগ বিচ্ছিন্ন রয়েছে। অনুগ্রহ করে সংযোগ পরীক্ষা করুন।"
                else "No internet connection. Please check your network."
            else ->
                if (language == SathiLanguage.BN) "দুঃখিত, এই মুহূর্তে উত্তর তৈরি করা যায়নি। একটু পর আবার চেষ্টা করুন।"
                else "Sorry, I couldn't complete that response right now. Please try again."
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
        offlineDownloadJob?.cancel()
        adminObserverJob?.cancel()
    }

    companion object {
        private const val TAG = "SathiViewModel"
    }
}
