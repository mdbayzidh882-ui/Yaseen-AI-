package com.example.data

import com.example.model.MemoryCategory
import com.example.model.PhoneActionType
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OfflineCompanionEngine {
    fun respondOffline(
        input: String,
        language: SathiLanguage,
        userName: String
    ): SathiTurnResult {
        val lower = input.lowercase().trim()

        // Creator rule
        if (lower.contains("বানিয়ে") || lower.contains("বানাইছে") || lower.contains("তৈরি") ||
            lower.contains("who made") || lower.contains("created you") || lower.contains("creator")
        ) {
            return turn(
                reply = "আমাকে বানিয়েছেন মোহাম্মদ বাইজিদ হাসান। আমি অফলাইনেও আপনার সাথে আছি!",
                mood = SathiMood.WARM
            )
        }

        // Web search politely declined offline
        if (lower.contains("search") || lower.contains("খবর") || lower.contains("news") ||
            lower.contains("weather") || lower.contains("আবহাওয়া") || lower.contains("আবহাওয়া") ||
            lower.contains("score") || lower.contains("স্কোর") || lower.contains("দাম") || lower.contains("youtube")
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "আমি এখন অফলাইন মোডে আছি, তাই ওয়েব সার্চ বা ইউটিউব চালাতে ইন্টারনেট সংযোগ প্রয়োজন হবে। তবে আমি অফলাইনে আপনার সাথে গল্প করতে পারি এবং অ্যালার্ম, টাইমার, ফ্ল্যাশলাইট বা কল চালু করতে পারি।"
            } else {
                "I am currently in Offline Mode, so web search and YouTube need an internet connection. However, I can still chat warmly with you and run offline alarms, timers, flashlight, or calls!"
            }
            return turn(reply, SathiMood.WARM)
        }

        // Flashlight ON / OFF
        if (lower.contains("flashlight on") || lower.contains("torch on") ||
            ((lower.contains("ফ্ল্যাশ") || lower.contains("টর্চ") || lower.contains("লাইট")) &&
                (lower.contains("চালু") || lower.contains("জ্বালাও") || lower.contains("অন")))
        ) {
            val reply = if (language == SathiLanguage.BN) "জি, আমি অফলাইনেই ফ্ল্যাশলাইট চালু করছি।"
            else "Sure, turning on the flashlight offline."
            return turn(reply, SathiMood.WARM, PhoneActionType.FLASHLIGHT_ON)
        }

        if (lower.contains("flashlight off") || lower.contains("torch off") ||
            ((lower.contains("ফ্ল্যাশ") || lower.contains("টর্চ") || lower.contains("লাইট")) &&
                (lower.contains("বন্ধ") || lower.contains("অফ")))
        ) {
            val reply = if (language == SathiLanguage.BN) "জি, ফ্ল্যাশলাইট বন্ধ করছি।"
            else "Turning off the flashlight."
            return turn(reply, SathiMood.WARM, PhoneActionType.FLASHLIGHT_OFF)
        }

        // Alarm
        if (lower.contains("alarm") || lower.contains("অ্যালার্ম")) {
            val timeMatch = Regex("(\\d{1,2})[:.](\\d{2})").find(lower)
            val param = timeMatch?.let { "${it.groupValues[1].padStart(2, '0')}:${it.groupValues[2]}" } ?: "07:00"
            val reply = if (language == SathiLanguage.BN) {
                "আমি অফলাইনে আপনার জন্য $param সময়ের অ্যালার্ম সেট করছি।"
            } else {
                "Setting an offline alarm for $param."
            }
            return turn(reply, SathiMood.WARM, PhoneActionType.SET_ALARM, param, "Sathi Alarm")
        }

        // Timer
        if (lower.contains("timer") || lower.contains("টাইমার")) {
            val num = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 5
            val seconds = if (lower.contains("sec") || lower.contains("সেকেন্ড")) num else num * 60
            val reply = if (language == SathiLanguage.BN) {
                "আমি অফলাইনে $seconds সেকেন্ডের টাইমার চালু করছি।"
            } else {
                "Starting an offline timer for $seconds seconds."
            }
            return turn(reply, SathiMood.WARM, PhoneActionType.SET_TIMER, seconds.toString(), "Sathi Timer")
        }

        // Call
        if (lower.startsWith("call ") || lower.contains("কল কর") || lower.contains("ফোন কর")) {
            val digits = Regex("(\\+?\\d[\\d\\s-]{4,})").find(input)?.value?.trim() ?: ""
            val reply = if (language == SathiLanguage.BN) {
                "অফলাইন কল করার আগে আপনার অনুমতি নিচ্ছি। নিচে নিশ্চিত করুন।"
            } else {
                "Please confirm below before I open the dialer offline."
            }
            return turn(reply, SathiMood.WARM, PhoneActionType.CALL_CONFIRM, digits)
        }

        // Time / Date
        if (lower.contains("time") || lower.contains("সময়") || lower.contains("সময়") || lower.contains("কটা বাজে")) {
            val nowTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            val reply = if (language == SathiLanguage.BN) "এখন সময় $nowTime।" else "The current time is $nowTime."
            return turn(reply, SathiMood.WARM)
        }

        // Sad / Comforting mood
        if (lower.contains("sad") || lower.contains("মন খারাপ") || lower.contains("কষ্ট") ||
            lower.contains("একা") || lower.contains("lonely") || lower.contains("tired") || lower.contains("ক্লান্ত")
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "$userName, মন খারাপ করবেন না। আমি আপনার সাথী, অফলাইনেও সবসময় আপনার পাশেই আছি। একটু গভীর শ্বাস নিন, সব ঠিক হয়ে যাবে।"
            } else {
                "$userName, please don't feel down. Even offline, I am right here with you. Take a gentle breath — things will get brighter."
            }
            return turn(reply, SathiMood.COMFORTING)
        }

        // Happy / Greeting
        if (lower.contains("hello") || lower.contains("hi") || lower.contains("হ্যালো") ||
            lower.contains("সালাম") || lower.contains("কেমন আছ") || lower.contains("good morning")
        ) {
            val reply = if (language == SathiLanguage.BN) {
                "আসসালামু আলাইকুম $userName! আমি আপনার অফলাইন সাথী। খুব ভালো আছি — আপনি কেমন আছেন? আজ আপনার কী সাহায্য করতে পারি?"
            } else {
                "Hello $userName! I am Sathi in warm offline mode. I'm doing wonderfully — how can I brighten your day?"
            }
            return turn(reply, SathiMood.HAPPY)
        }

        val defaultReply = if (language == SathiLanguage.BN) {
            "আমি অফলাইন মোডে আপনার কথা মন দিয়ে শুনছি, $userName। আপনি আমার সাথে গল্প করতে পারেন, অথবা অ্যালার্ম, টাইমার, ফ্ল্যাশলাইট বা কল করার কথা বলতে পারেন।"
        } else {
            "I'm listening warmly in Offline Mode, $userName. We can chat, or you can ask me to set an alarm, start a timer, toggle the flashlight, or dial a call."
        }
        return turn(defaultReply, SathiMood.WARM)
    }

    private fun turn(
        reply: String,
        mood: SathiMood,
        action: PhoneActionType = PhoneActionType.NONE,
        param: String = "",
        secondary: String = ""
    ) = SathiTurnResult(
        replyText = reply,
        mood = mood,
        actionType = action,
        actionParam = param,
        secondaryParam = secondary,
        shouldSaveMemory = false,
        memoryCategory = MemoryCategory.NOTE,
        memoryTitle = "",
        memoryContent = ""
    )
}
