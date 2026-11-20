package com.example.data

import com.example.model.SathiLanguage
import com.example.model.WebSource
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class WebSearchService {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun searchWeb(query: String, language: SathiLanguage): List<WebSource> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()

            val results = mutableListOf<WebSource>()
            val lower = trimmed.lowercase()

            // 1. Live weather via Open-Meteo for Dhaka or common weather queries
            if (lower.contains("weather") || lower.contains("আবহাওয়া") || lower.contains("আবহাওয়া") ||
                lower.contains("temperature") || lower.contains("তাপমাত্রা") || lower.contains("বৃষ্টি")
            ) {
                fetchWeatherSource(trimmed, language)?.let { results.add(it) }
            }

            // 2. Wikipedia search (Bengali or English) for current facts, news topics, sports, people
            val wikiLang = if (language == SathiLanguage.BN || trimmed.any { it in '\u0980'..'\u09FF' }) "bn" else "en"
            results.addAll(fetchWikipediaSources(trimmed, wikiLang))

            if (results.isEmpty() && wikiLang == "bn") {
                results.addAll(fetchWikipediaSources(trimmed, "en"))
            }

            // 3. DuckDuckGo Instant Answer fallback
            if (results.size < 2) {
                fetchDuckDuckGoSource(trimmed)?.let { results.add(it) }
            }

            results.distinctBy { it.url }.take(4)
        }

    private fun fetchWeatherSource(query: String, language: SathiLanguage): WebSource? {
        return try {
            val lower = query.lowercase()
            val (cityName, lat, lon) = when {
                lower.contains("chittagong") || lower.contains("chattogram") || lower.contains("চট্টগ্রাম") ->
                    Triple("Chattogram / চট্টগ্রাম", 22.3569, 91.7832)
                lower.contains("sylhet") || lower.contains("সিলেট") ->
                    Triple("Sylhet / সিলেট", 24.8949, 91.8687)
                lower.contains("rajshahi") || lower.contains("রাজশাহী") ->
                    Triple("Rajshahi / রাজশাহী", 24.3745, 88.6042)
                lower.contains("khulna") || lower.contains("খুলনা") ->
                    Triple("Khulna / খুলনা", 22.8456, 89.5403)
                lower.contains("kolkata") || lower.contains("কলকাতা") ->
                    Triple("Kolkata / কলকাতা", 22.5726, 88.3639)
                else -> Triple("Dhaka / ঢাকা", 23.8103, 90.4125)
            }
            val url =
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,wind_speed_10m"
            val req = Request.Builder().url(url).header("User-Agent", "SathiAndroidApp/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val current = JSONObject(body).optJSONObject("current") ?: return null
                val temp = current.optDouble("temperature_2m", Double.NaN)
                val humidity = current.optInt("relative_humidity_2m", -1)
                val wind = current.optDouble("wind_speed_10m", Double.NaN)
                if (temp.isNaN()) return null

                val title = if (language == SathiLanguage.BN) "$cityName - লাইভ আবহাওয়া" else "$cityName Live Weather"
                val snippet = if (language == SathiLanguage.BN) {
                    "বর্তমান তাপমাত্রা ${temp}°C, আর্দ্রতা ${humidity}% এবং বাতাসের গতি ${wind} km/h।"
                } else {
                    "Current temperature is ${temp}°C, humidity ${humidity}%, wind speed ${wind} km/h."
                }
                WebSource(
                    title = title,
                    snippet = snippet,
                    url = "https://open-meteo.com/"
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchWikipediaSources(query: String, langCode: String): List<WebSource> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url =
                "https://$langCode.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&srlimit=3&utf8=1"
            val req = Request.Builder().url(url).header("User-Agent", "SathiAndroidApp/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val searchArray = JSONObject(body)
                    .optJSONObject("query")
                    ?.optJSONArray("search") ?: return emptyList()

                buildList {
                    for (i in 0 until searchArray.length()) {
                        val item = searchArray.getJSONObject(i)
                        val title = item.optString("title").trim()
                        val rawSnippet = item.optString("snippet")
                            .replace(Regex("<[^>]*>"), "")
                            .replace("&quot;", "\"")
                            .replace("&amp;", "&")
                            .trim()
                        if (title.isNotEmpty()) {
                            val pageUrl = "https://$langCode.wikipedia.org/wiki/${URLEncoder.encode(title.replace(" ", "_"), "UTF-8")}"
                            add(WebSource(title = title, snippet = rawSnippet, url = pageUrl))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchDuckDuckGoSource(query: String): WebSource? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val req = Request.Builder().url(url).header("User-Agent", "SathiAndroidApp/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val abstractText = json.optString("AbstractText").trim()
                val abstractUrl = json.optString("AbstractURL").trim()
                val heading = json.optString("Heading").trim()
                if (abstractText.isNotEmpty() && abstractUrl.isNotEmpty()) {
                    WebSource(
                        title = heading.ifBlank { query },
                        snippet = abstractText.take(280),
                        url = abstractUrl
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
