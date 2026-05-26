package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiOutreachService {
    private const val TAG = "GeminiOutreach"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Attempts to query the Gemini API to construct beautiful personalized outreach messages.
     * Falls back to high-quality procedural generation if API key is a placeholder or fails.
     */
    suspend fun generateOutreach(
        contact: Contact,
        interactions: List<Interaction>
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        val isKeyConfigured = apiKey.isNotEmpty() &&
                !apiKey.startsWith("MY_") &&
                apiKey != "placeholder" &&
                apiKey != "GEMINI_API_KEY"

        if (!isKeyConfigured) {
            Log.w(TAG, "Gemini API key is not configured. Using local smart prompt engine fallback.")
            return@withContext getLocalFallbackOutreach(contact, interactions)
        }

        val prompt = buildOutreachPrompt(contact, interactions)
        
        try {
            // Build the native JSON payload
            val root = JSONObject()
            val contentsArray = JSONArray()
            val contentObject = JSONObject()
            val partsArray = JSONArray()
            val partObject = JSONObject()
            
            partObject.put("text", prompt)
            partsArray.put(partObject)
            contentObject.put("parts", partsArray)
            contentsArray.put(contentObject)
            root.put("contents", contentsArray)

            // Optional: System instructions
            val systemInstruction = JSONObject()
            val siParts = JSONArray()
            val siPart = JSONObject()
            siPart.put("text", "You are an elite, highly empathetic personal relationship advisor. Your goal is to help users maintain and deepen their connections with others. Draft messages that sound extremely natural, authentic, and customized to the relationship context.")
            siParts.put(siPart)
            systemInstruction.put("parts", siParts)
            root.put("systemInstruction", systemInstruction)

            // Set temperature/config
            val genConfig = JSONObject()
            genConfig.put("temperature", 0.7)
            root.put("generationConfig", genConfig)

            val requestBodyJson = root.toString()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestBodyJson.toRequestBody(mediaType)

            val urlWithKey = "$BASE_URL?key=$apiKey"
            val request = Request.Builder()
                .url(urlWithKey)
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Request failed with code ${response.code}: $errBody")
                    return@withContext "API request failed: (${response.code}). Falling back to local templates:\n\n" + getLocalFallbackOutreach(contact, interactions)
                }

                val responseBodyStr = response.body?.string() ?: return@withContext "Error: Received empty response from Gemini API"
                val jsonResponse = JSONObject(responseBodyStr)
                
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val textValue = parts.getJSONObject(0).optString("text", "")
                            if (textValue.isNotEmpty()) {
                                return@withContext textValue.trim()
                            }
                        }
                    }
                }
                return@withContext "Could not extract suggestions. Fallback:\n\n" + getLocalFallbackOutreach(contact, interactions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini API outreach generation", e)
            return@withContext "Network Error: ${e.localizedMessage}. Fallback:\n\n" + getLocalFallbackOutreach(contact, interactions)
        }
    }

    private fun buildOutreachPrompt(contact: Contact, interactions: List<Interaction>): String {
        val interactionsText = if (interactions.isNotEmpty()) {
            interactions.take(5).joinToString("\n") { 
                val timeStr = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp))
                "- ${it.type} ($timeStr): ${it.summary}"
            }
        } else {
            "No logged interaction history yet."
        }
        
        return """
            Please help me draft 3 warm, natural-sounding, personalized outreach message options (icebreakers) to catch up with my contact.
            
            Contact Info:
            - Name: ${contact.name}
            - Job Title / Company: ${contact.company.ifEmpty { "Not specified" }}
            - Location: ${contact.location.ifEmpty { "Not specified" }}
            - How I know them / Met: ${contact.howMet.ifEmpty { "Not specified" }}
            - Relationship Closeness Score: ${contact.closenessScore}/5
            - Area of Interest / Memory Notes: ${contact.notes.ifEmpty { "None recorded yet" }}
            - Tags: ${contact.tags.ifEmpty { "None" }}
            
            Last Logged Interactions:
            $interactionsText
            
            Provide exactly 3 distinct outreach styles formatted beautifully:
            1. 🌿 **CASUAL & FRIENDLY** (Short, lighthearted, easy to reply on WhatsApp or iMessage)
            2. 💼 **PROFESSIONAL & VALUABLE** (Tailored to their career or work angle, highlighting high support and networking value)
            3. 🧠 **CONTEXT-AWARE CATCHUP** (Sincere reaching out focusing on how we met or our last logged interaction summary)

            Make them fully written and ready-to-send (do NOT use bracketed placeholders like [Name] or [Job] - use actual context data or frame it without variables). Write in first person ("I", "you"). Keep each under 4 sentences. Break down clean using headers.
        """.trimIndent()
    }

    private fun getLocalFallbackOutreach(contact: Contact, interactions: List<Interaction>): String {
        val name = contact.name.split(" ").firstOrNull() ?: contact.name
        val companyText = if (contact.company.isNotEmpty()) " at ${contact.company}" else ""
        val lastMeetingText = if (contact.howMet.isNotEmpty()) " from ${contact.howMet}" else ""
        
        val lastIntSummary = interactions.firstOrNull()?.summary ?: ""
        val contextSnippet = if (lastIntSummary.isNotEmpty()) {
            "loved hearing about '$lastIntSummary' last time we caught up"
        } else if (contact.notes.isNotEmpty()) {
            "remembered you mentioning that you're focusing on '${contact.notes.take(50)}'"
        } else {
            "wanted to loop back and see how life has been going lately"
        }

        return """
            ⚠️ [API Key not active: Using offline intelligence templates]

            🌿 1. CASUAL & FRIENDLY (WhatsApp/Text)
            "Hey $name! Hope you're doing great. It's been a little while - wanted to check in and see how everything is going on your end. We should grab a coffee or hopping on a quick catch-up call sometime soon if you're free!"

            💼 2. PROFESSIONAL & VALUABLE (LinkedIn/Email)
            "Hi $name, hope you're having a productive week$companyText. I've been following some of the recent trends in your sector and it made me think of your work. Let's find some time to sync up soon—I'd love to hear what projects you are driving right now and see if I can support in any way."

            🧠 3. CONTEXT-AWARE CATCHUP (Personalised message)
            "Hey $name! I was just reflecting on $lastMeetingText and I $contextSnippet. Let me know when you have 15 mins for a quick call. I'd love to hear how that's developing!"
        """.trimIndent()
    }
}
