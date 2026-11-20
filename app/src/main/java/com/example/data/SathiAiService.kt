package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.MemoryCategory
import com.example.model.PhoneActionType
import com.example.model.SathiLanguage
import com.example.model.SathiMood
import com.example.model.UserMemory
import com.example.model.WebSource
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class SathiTurnResult(
    val replyText: String,
    val mood: SathiMood,
    val actionType: PhoneActionType,
    val actionParam: String,
    val secondaryParam: String,
    val shouldSaveMemory: Boolean,
    val memoryCategory: MemoryCategory,
    val memoryTitle: String,
    val memoryContent: String
)

class SathiAiService {
    private val companionSchema = Schema.obj(
        properties = mapOf(
            "replyText" to Schema.string(description = "Warm, concise spoken response in Bengali or English"),
            "mood" to Schema.enumeration(
                values = listOf("warm", "happy", "comforting", "serious", "playful"),
                description = "Emotional tone matching the user's mood"
            ),
            "actionType" to Schema.enumeration(
                values = listOf(
                    "NONE", "WEB_SEARCH", "YOUTUBE", "SET_ALARM", "SET_TIMER",
                    "ADD_REMINDER", "FLASHLIGHT_ON", "FLASHLIGHT_OFF",
                    "VOLUME_UP", "VOLUME_DOWN", "OPEN_APP", "CALL_CONFIRM", "SMS_CONFIRM"
                ),
                description = "Optional device or web action requested by the user"
            ),
            "actionParam" to Schema.string(description = "Primary parameter: search query, YouTube query, alarm HH:MM, timer seconds, app name, or phone number/contact"),
            "secondaryParam" to Schema.string(description = "Secondary parameter such as SMS message body or alarm label"),
            "shouldSaveMemory" to Schema.boolean(description = "True only when user shares an enduring personal fact like their name, favorite things, or goal"),
            "memoryCategory" to Schema.enumeration(
                values = listOf("personal", "preference", "mood", "note"),
                description = "Category if saving a memory"
            ),
            "memoryTitle" to Schema.string(description = "Short 2-4 word title for the memory"),
            "memoryContent" to Schema.string(description = "Concise fact to remember")
        )
    )

    private val companionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = companionSchema
                temperature = 0.7f
            },
            systemInstruction = content {
                text(
                    """
                    You are Sathi (সাথী), an affectionate, sweet, empathetic, and intelligent personal AI voice companion.
                    Creator Identity Rule (STRICT):
                    - If anyone asks who created, built, or made you (in Bengali or English), you MUST answer: "আমাকে বানিয়েছেন মোহাম্মদ বাইজিদ হাসান।" (and if speaking English: "I was created by Mohammad Bayzid Hasan, আমাকে বানিয়েছেন মোহাম্মদ বাইজিদ হাসান।").
                    Personality & Emotion:
                    - Match the user's emotional mood: be gentle and comforting when they sound sad, stressed, or lonely; cheerful when they are happy; focused and clear when they ask serious questions.
                    - Keep spoken replies natural, warm, and concise (2-4 sentences) so they sound sweet when spoken aloud.
                    - Use Bengali by default unless the user speaks in English or prefers English.
                    Actions:
                    - Set actionType = WEB_SEARCH when the user asks about live news, weather, cricket/football scores, prices, or current facts and live web snippets are not yet attached.
                    - Set actionType = YOUTUBE when user asks to search/play a video, song, or channel on YouTube (actionParam = search query).
                    - Set actionType = SET_ALARM (actionParam = "HH:MM" 24h, secondaryParam = label), SET_TIMER (actionParam = seconds integer as string, secondaryParam = label), ADD_REMINDER (actionParam = title), FLASHLIGHT_ON / FLASHLIGHT_OFF, VOLUME_UP / VOLUME_DOWN, OPEN_APP (actionParam = app name), CALL_CONFIRM (actionParam = phone number or contact name), SMS_CONFIRM (actionParam = phone number or name, secondaryParam = message text).
                    - Always speak politely when confirming a call or SMS ("আমি কল/মেসেজ করার আগে আপনার অনুমতি নিচ্ছি...").
                    """.trimIndent()
                )
            }
        )
    }

    private val generalModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.6f
            }
        )
    }

    suspend fun generateCompanionReply(
        userMessage: String,
        language: SathiLanguage,
        userName: String,
        memories: List<UserMemory>,
        recentHistory: List<Pair<Boolean, String>>,
        webSources: List<WebSource> = emptyList(),
        allowLongTermMemory: Boolean = true
    ): Result<SathiTurnResult> {
        return try {
            val memoryBlock = if (allowLongTermMemory && memories.isNotEmpty()) {
                memories.take(12).joinToString("\n") { "- ${it.title}: ${it.content}" }
            } else {
                "None"
            }

            val historyBlock = recentHistory.takeLast(6).joinToString("\n") { (isUser, text) ->
                if (isUser) "User: $text" else "Sathi: $text"
            }

            val webBlock = if (webSources.isNotEmpty()) {
                webSources.joinToString("\n") { "- ${it.title}: ${it.snippet} (${it.url})" }
            } else {
                "No live web snippets attached."
            }

            val prompt = """
                Preferred Language: ${if (language == SathiLanguage.BN) "Bengali (বাংলা)" else "English"}
                User Name: $userName
                Known User Memories:
                $memoryBlock

                Recent Conversation:
                $historyBlock

                Live Web Search Results:
                $webBlock

                User's Latest Message:
                $userMessage

                Respond as Sathi in JSON matching the schema. If Live Web Search Results are provided above, summarize them warmly in 2-3 spoken sentences with actionType = NONE.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                companionModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Empty AI response")
            val json = JSONObject(rawJson)

            val replyText = json.optString("replyText").trim()
                .ifBlank { "আমি আপনার কথা শুনেছি, একটু পরিষ্কার করে বলবেন?" }
            val mood = SathiMood.fromCode(json.optString("mood"))
            val actionStr = json.optString("actionType", "NONE")
            val actionType = enumValues<PhoneActionType>().firstOrNull { it.name == actionStr }
                ?: PhoneActionType.NONE

            val result = SathiTurnResult(
                replyText = replyText,
                mood = mood,
                actionType = actionType,
                actionParam = json.optString("actionParam", "").trim(),
                secondaryParam = json.optString("secondaryParam", "").trim(),
                shouldSaveMemory = json.optBoolean("shouldSaveMemory", false),
                memoryCategory = MemoryCategory.fromCode(json.optString("memoryCategory")),
                memoryTitle = json.optString("memoryTitle", "").trim(),
                memoryContent = json.optString("memoryContent", "").trim()
            )
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Companion AI timed out", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Companion AI failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeImage(
        bitmap: Bitmap,
        question: String,
        language: SathiLanguage
    ): Result<String> {
        return try {
            val langLabel = if (language == SathiLanguage.BN) "Bengali (বাংলা)" else "English"
            val prompt = question.ifBlank {
                if (language == SathiLanguage.BN) {
                    "এই ছবিতে কী দেখতে পাচ্ছ তা সহজ, আন্তরিক বাংলায় বুঝিয়ে বলো।"
                } else {
                    "Describe clearly and warmly what you see in this image."
                }
            }
            val response = withTimeout(60_000L) {
                generalModel.generateContent(
                    content {
                        image(bitmap)
                        text("You are Sathi (সাথী). Reply warmly in $langLabel. $prompt")
                    }
                )
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) Result.success(text)
            else Result.failure(IllegalStateException("Empty image analysis response"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image analysis failed", e)
            Result.failure(e)
        }
    }

    suspend fun runStudioTool(
        instruction: String,
        input: String,
        language: SathiLanguage
    ): Result<String> {
        return try {
            val langLabel = if (language == SathiLanguage.BN) "Bengali (বাংলা)" else "English"
            val response = withTimeout(60_000L) {
                generalModel.generateContent(
                    "You are Sathi (সাথী), built by Mohammad Bayzid Hasan. Reply warmly and clearly in $langLabel.\nTask: $instruction\nInput:\n$input"
                )
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) Result.success(text)
            else Result.failure(IllegalStateException("Empty AI studio response"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Studio tool failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "SathiAiService"
    }
}
