package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SathiLanguage(val code: String, val labelBn: String, val labelEn: String) {
    BN("bn", "বাংলা", "Bengali"),
    EN("en", "English", "English");

    companion object {
        fun fromCode(raw: String?): SathiLanguage =
            entries.firstOrNull { it.code == raw } ?: BN
    }
}

enum class SathiVoiceProfile(val code: String, val labelBn: String, val labelEn: String) {
    FEMALE("female", "মিষ্টি মেয়ে কণ্ঠ", "Sweet Female Voice"),
    MALE("male", "মার্জিত ছেলে কণ্ঠ", "Warm Male Voice");

    companion object {
        fun fromCode(raw: String?): SathiVoiceProfile =
            entries.firstOrNull { it.code == raw } ?: FEMALE
    }
}

enum class SathiMood(val code: String, val labelBn: String, val labelEn: String) {
    WARM("warm", "আন্তরিক", "Warm"),
    HAPPY("happy", "আনন্দিত", "Happy"),
    COMFORTING("comforting", "সান্ত্বনাময়", "Comforting"),
    SERIOUS("serious", "মনোযোগী", "Serious"),
    PLAYFUL("playful", "প্রাণবন্ত", "Playful");

    companion object {
        fun fromCode(raw: String?): SathiMood =
            entries.firstOrNull { it.code == raw } ?: WARM
    }
}

enum class PlanTier(val code: String, val labelBn: String, val labelEn: String) {
    FREE("free", "ফ্রি প্ল্যান", "Free Plan"),
    PREMIUM("premium", "প্রিমিয়াম প্ল্যান", "Premium Plan"),
    OWNER("owner", "অনার (আনলিমিটেড)", "Owner (Unlimited)");

    companion object {
        fun fromCode(raw: String?): PlanTier =
            entries.firstOrNull { it.code == raw } ?: FREE
    }
}

enum class CodeType(val code: String, val labelBn: String, val labelEn: String, val durationMs: Long) {
    ONE_MONTH("1_month", "১ মাস", "1 Month", 30L * 24 * 60 * 60 * 1000),
    SIX_MONTHS("6_months", "৬ মাস", "6 Months", 180L * 24 * 60 * 60 * 1000),
    ONE_YEAR("1_year", "১ বছর", "1 Year", 365L * 24 * 60 * 60 * 1000),
    OWNER("owner", "অনার কোড (আজীবন)", "Owner Code (Permanent)", 0L);

    companion object {
        fun fromCode(raw: String?): CodeType =
            entries.firstOrNull { it.code == raw } ?: ONE_MONTH
    }
}

enum class PhoneActionType {
    NONE,
    WEB_SEARCH,
    YOUTUBE,
    SET_ALARM,
    SET_TIMER,
    ADD_REMINDER,
    FLASHLIGHT_ON,
    FLASHLIGHT_OFF,
    VOLUME_UP,
    VOLUME_DOWN,
    OPEN_APP,
    CALL_CONFIRM,
    SMS_CONFIRM
}

enum class MemoryCategory(val code: String, val labelBn: String, val labelEn: String) {
    PERSONAL("personal", "ব্যক্তিগত", "Personal"),
    PREFERENCE("preference", "পছন্দ", "Preference"),
    MOOD("mood", "অনুভূতি", "Mood"),
    NOTE("note", "নোট", "Note");

    companion object {
        fun fromCode(raw: String?): MemoryCategory =
            entries.firstOrNull { it.code == raw } ?: NOTE
    }
}

data class WebSource(
    val title: String = "",
    val snippet: String = "",
    val url: String = ""
)

data class PendingConfirmation(
    val type: PhoneActionType,
    val target: String,
    val messageBody: String = ""
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val mood: SathiMood = SathiMood.WARM,
    val sources: List<WebSource> = emptyList(),
    val actionLabel: String? = null,
    val isOfflineReply: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val email: String = "",
    val plan: String = "free",
    val redeemedCodeId: String = "",
    val planExpiresAt: Long = 0L,
    val banned: Boolean = false,
    val preferredLanguage: String = "bn",
    val preferredVoice: String = "female",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun effectivePlan(): PlanTier {
        val tier = PlanTier.fromCode(plan)
        if (tier == PlanTier.PREMIUM && planExpiresAt > 0L && System.currentTimeMillis() > planExpiresAt) {
            return PlanTier.FREE
        }
        return tier
    }

    fun hasPremiumAccess(): Boolean = effectivePlan() != PlanTier.FREE
}

@Keep
@IgnoreExtraProperties
data class UserMemory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "note",
    val title: String = "",
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PremiumCode(
    @DocumentId val id: String = "",
    val codeType: String = "1_month",
    val status: String = "active",
    val createdBy: String = "",
    val usedBy: String = "",
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppConfig(
    @DocumentId val id: String = "global",
    val cameraEnabledForFree: Boolean = false,
    val longTermMemoryForFree: Boolean = false,
    val translationForFree: Boolean = false,
    val dayPlanningForFree: Boolean = false,
    val announcementText: String = "",
    val announcementActive: Boolean = false,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ActivityLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val target: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
