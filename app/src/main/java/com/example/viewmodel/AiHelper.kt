package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object AiHelper {
    private const val TAG = "AiHelper"
    // Modern supported Gemini models; removed discontinued gemini-2.5-flash
    private val CANDIDATE_MODELS = listOf(
        "gemini-3.8-flash",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-flash-latest"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getSavedApiKey(context: Context): String {
        val prefs = context.getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
        return prefs.getString("custom_gemini_api_key", "") ?: ""
    }

    fun saveApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences("app_profile_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_gemini_api_key", key.trim()).apply()
    }

    private fun extractTextFromDocx(bytes: ByteArray): String? {
        return try {
            val bais = ByteArrayInputStream(bytes)
            val zis = ZipInputStream(bais)
            var entry = zis.nextEntry
            val textBuilder = StringBuilder()
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zis.bufferedReader(Charsets.UTF_8).readText()
                    val regex = Regex("<w:t[^>]*>(.*?)</w:t>")
                    regex.findAll(xml).forEach { match ->
                        textBuilder.append(match.groupValues[1]).append(" ")
                    }
                    break
                }
                entry = zis.nextEntry
            }
            zis.close()
            textBuilder.toString().trim().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun parseTimetableData(
        rawText: String,
        image: Bitmap?,
        apiKey: String,
        fileBytes: ByteArray? = null,
        fileMimeType: String? = null,
        userRole: String = "teacher"
    ): String? {
        return withContext(Dispatchers.IO) {
            val resolvedApiKey = when {
                apiKey.isNotBlank() && apiKey != "YOUR_API_KEY_HERE" && !apiKey.contains("AIzaSyDwM0mg") -> apiKey.trim()
                com.example.BuildConfig.GEMINI_API_KEY.isNotBlank() && com.example.BuildConfig.GEMINI_API_KEY != "YOUR_API_KEY_HERE" && !com.example.BuildConfig.GEMINI_API_KEY.contains("AIzaSyDwM0mg") -> com.example.BuildConfig.GEMINI_API_KEY.trim()
                else -> apiKey.trim().ifBlank { com.example.BuildConfig.GEMINI_API_KEY.trim() }
            }

            val roleGuidance = if (userRole.equals("student", ignoreCase = true)) {
                """
                ROLE: You are extracting academic timetable and study routine data for a STUDENT.
                The student is providing class routine text, syllabus, photo/screenshot of routine, or timetable notes.
                Extract all courses/subjects the student attends and map each into our schema:
                - Each subject the student attends becomes an entry in "batches":
                  - "course": { 
                      "name": "Full Course/Subject Name", 
                      "code": "Course Code (e.g. CS301) or null",
                      "shortName": "Short abbreviation/acronym (e.g. IBE for Introduction to Biology for Engineers, DBMS, OS, DSA)"
                    }
                  - "section": Student's section or class (e.g. "Section B", "Semester 3") or null
                  - "location": Default lecture hall/room/lab (e.g. "Room 204", "Physics Lab") or null
                  - "weeklySchedule": Array of weekly class timings:
                    - "day": Day of the week (e.g. "Monday", "Tuesday", etc.)
                    - "time": Class timing (e.g. "09:00 - 10:00 AM", "02:00 PM - 03:00 PM")
                    - "location": Room/Lab if specific to that slot or null
                  - "students": [] (empty array for students)
                - "teacher": Instructor/Professor name if mentioned in the input, or null
                """.trimIndent()
            } else {
                """
                ROLE: You are extracting academic timetable data for a TEACHER / FACULTY member.
                The teacher is providing class schedules, student rosters, timetable photos, or notes.
                Extract all batches the teacher conducts:
                - Every unique combination of course + section/batch is ONE batch entry in "batches":
                  - "course": { 
                      "name": "Full Course/Subject Name", 
                      "code": "Course Code (e.g. CS301) or null",
                      "shortName": "Short abbreviation/acronym (e.g. IBE for Introduction to Biology for Engineers, DBMS, OS, DSA)"
                    }
                  - "year": Academic year or null
                  - "semester": Semester or null
                  - "section": Section or Class name (e.g. "CSE-A", "Class 10")
                  - "location": Default classroom/lab or null
                  - "weeklySchedule": Array of weekly class timings (day, time, location)
                  - "students": Array of student objects { "name": string, "rollNumber": string|null, "email": string|null } if a student list is provided; otherwise []. If email is not present in the input, leave email as null.
                - "teacher": Teacher's own name and ID if mentioned, or null
                """.trimIndent()
            }

            val systemPrompt = """
                $roleGuidance

                Universal Extraction Guidelines:
                1. ANALYZE AND FIT: Fit whatever information is present in the input into our structure.
                   Even if the user provides informal or partial notes (e.g. "Maths Mon 9am room 101, Physics Wed 11am"), extract them into batches and weekly schedules accurately.

                2. SUBJECT ABBREVIATION & SHORT CODES:
                   - When encountering a long subject name (e.g. "Introduction to Biology for Engineers"), generate a clean short abbreviation/acronym (e.g. "IBE", "DBMS", "OS", "DSA", "SE", "CN") and store it in the "shortName" field.
                   - If a subject code (e.g. "CS301") is already present, reuse that as the "code" field.

                3. AM/PM TIME PARSING & INFERENCE RULE:
                   When a time in the source timetable does NOT explicitly specify AM or PM, infer it using this rule:
                   Assume all class times fall within a typical academic day window of 6:00 AM to 6:00 PM (18:00). Apply standard 12-hour clock logic within that window:
                   - Hours 6 through 11 (e.g. "06:00", "09:00", "11:00") -> AM.
                   - Hour 12 (e.g. "12:00") -> PM (noon).
                   - Hours 1 through 6 written as "01:00" through "06:00" appearing AFTER a 12:00 or after clearly-morning entries in the same day's sequence -> PM (e.g. "02:00 to 04:00" -> "02:00 PM - 04:00 PM", "12:00 to 1:00" -> "12:00 PM - 01:00 PM").
                   - Use the sequence/order of classes within the same day as a consistency check — times should generally increase through the day (morning -> afternoon -> evening); if a literal interpretation would make a later-listed class appear earlier than an earlier-listed one, prefer the interpretation that keeps the day's sequence chronological.

                4. BREAK & FREE TIME HANDLING:
                   DO NOT assume a time gap between classes is automatically a lunch/meal break — only label a slot as "Break" or "Lunch" if the source timetable EXPLICITLY labels it as such (e.g. a cell literally says "Lunch," "Break," or spells it out like "L-U-N-C-H" across the row). If there is a gap in the schedule with NO explicit break label, extract it as an unscheduled/free gap — represent this by simply NOT creating a class entry for that slot (leave it out of weeklySchedule rather than inventing a "Break" entry), so the app's UI can show it as "Free" time based on the absence of a scheduled class, not a guessed label.

                5. IDENTIFY MISSING FIELDS:
                   Identify any fields from the standard structure that could NOT be found or were incomplete (such as missing section, missing room/location, missing timings, missing student list, missing teacher name, etc.).
                   List each missing field clearly in the "missingFields" string array (e.g. ["Location missing for Physics", "Section not specified", "Student list not provided"]).

                6. PROVIDE HELPFUL SUMMARY:
                   In the "summary" string field, provide a clear, concise summary in natural language explaining what was extracted and which fields were missing or need the user's attention.

                7. VAGUE / INVALID INPUT HANDLING:
                   If the input contains no recognizable classes, subjects, or timetable information (e.g. random text like "gyy"), DO NOT throw an error. Instead, return:
                   - "batches": []
                   - "teacher": null
                   - "missingFields": ["timetable_data", "subject_names", "class_timings", "schedule_days"]
                   - "summary": "No classes, subjects, or timings could be recognized from the input. Please enter subject names, days, and times, or upload a timetable photo."

                8. Output MUST strictly be valid JSON matching this schema:
                {
                  "teacher": { "name": string|null, "id": string|null },
                  "batches": [
                    {
                      "batchId": string|null,
                      "year": string|null,
                      "semester": string|null,
                      "course": { "code": string|null, "name": string, "shortName": string|null },
                      "section": string|null,
                      "location": string|null,
                      "weeklySchedule": [
                        { "day": string, "time": string|null, "location": string|null }
                      ],
                      "students": [
                        { "id": string|null, "name": string, "rollNumber": string|null, "email": string|null }
                      ]
                    }
                  ],
                  "missingFields": [string],
                  "summary": string
                }
            """.trimIndent()

            // Construct JSON request body for Gemini REST API
            val partsArray = JSONArray()

            var extractedDocText = ""
            if (fileBytes != null && fileBytes.isNotEmpty()) {
                val isDocx = fileBytes.size > 4 && fileBytes[0] == 'P'.code.toByte() && fileBytes[1] == 'K'.code.toByte()
                val isPdf = fileBytes.size > 4 && fileBytes[0] == '%'.code.toByte() && fileBytes[1] == 'P'.code.toByte() && fileBytes[2] == 'D'.code.toByte() && fileBytes[3] == 'F'.code.toByte()

                if (isDocx) {
                    val docxText = extractTextFromDocx(fileBytes)
                    if (!docxText.isNullOrBlank()) {
                        extractedDocText = "\n\n[Attached Word Document Content]:\n$docxText"
                    }
                } else if (isPdf || (fileMimeType?.contains("pdf", ignoreCase = true) == true)) {
                    try {
                        val base64Doc = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
                        val inlineData = JSONObject()
                            .put("mimeType", "application/pdf")
                            .put("data", base64Doc)
                        partsArray.put(JSONObject().put("inlineData", inlineData))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to encode PDF to base64", e)
                    }
                } else if (fileMimeType?.startsWith("image/", ignoreCase = true) == true) {
                    try {
                        val base64Image = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
                        val inlineData = JSONObject()
                            .put("mimeType", fileMimeType)
                            .put("data", base64Image)
                        partsArray.put(JSONObject().put("inlineData", inlineData))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to encode image to base64", e)
                    }
                } else {
                    try {
                        val txt = String(fileBytes, Charsets.UTF_8).trim()
                        if (txt.isNotBlank() && txt.all { it.code in 9..126 || it.code in 160..65535 }) {
                            extractedDocText = "\n\n[Attached File Content]:\n$txt"
                        }
                    } catch (_: Exception) {}
                }
            }

            // 1. Text input
            val basePrompt = rawText.ifBlank { "Extract timetable and schedule from this input." }
            val promptText = basePrompt + extractedDocText
            val textPart = JSONObject().put("text", promptText)
            partsArray.put(textPart)

            // 2. Image input (if provided)
            if (image != null) {
                try {
                    val stream = ByteArrayOutputStream()
                    image.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    val inlineData = JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", base64Image)
                    partsArray.put(JSONObject().put("inlineData", inlineData))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to encode image to base64", e)
                }
            }

            val contentsArray = JSONArray()
                .put(JSONObject().put("parts", partsArray))

            val systemInstruction = JSONObject()
                .put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))

            val generationConfig = JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", 0.1)

            val requestJson = JSONObject()
                .put("contents", contentsArray)
                .put("systemInstruction", systemInstruction)
                .put("generationConfig", generationConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            var lastError: String? = null

            for (model in CANDIDATE_MODELS) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$resolvedApiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                try {
                    Log.d(TAG, "Executing Gemini API request with model $model...")
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        var parsedMessage = ""
                        try {
                            val errJson = JSONObject(responseBody).optJSONObject("error")
                            parsedMessage = errJson?.optString("message") ?: ""
                        } catch (_: Exception) {}

                        val errMsg = when {
                            response.code == 404 -> "Model $model is no longer available (404)."
                            response.code == 403 && (parsedMessage.contains("leaked", ignoreCase = true) || parsedMessage.contains("disabled", ignoreCase = true) || parsedMessage.contains("API key", ignoreCase = true)) ->
                                "Gemini API key is invalid or Generative Language API is disabled. Please configure a valid Gemini API key."
                            parsedMessage.isNotBlank() -> parsedMessage
                            else -> "Gemini API HTTP ${response.code} ($model)"
                        }
                        Log.e(TAG, "Gemini call failed for $model: $errMsg")
                        lastError = errMsg
                        continue
                    }

                    val jsonResp = JSONObject(responseBody)
                    val candidates = jsonResp.optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) {
                        lastError = "No candidates returned by Gemini ($model)"
                        continue
                    }

                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    var textOutput = parts?.optJSONObject(0)?.optString("text") ?: ""

                    // Clean markdown formatting if present
                    if (textOutput.contains("```json")) {
                        textOutput = textOutput.substringAfter("```json").substringBeforeLast("```")
                    } else if (textOutput.contains("```")) {
                        textOutput = textOutput.substringAfter("```").substringBeforeLast("```")
                    }
                    textOutput = textOutput.trim()

                    val startIndex = textOutput.indexOf('{')
                    val endIndex = textOutput.lastIndexOf('}')
                    if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
                        textOutput = textOutput.substring(startIndex, endIndex + 1)
                    }

                    if (textOutput.startsWith("{") && textOutput.endsWith("}")) {
                        Log.d(TAG, "Gemini timetable extraction successful! Length: ${textOutput.length}")
                        return@withContext textOutput
                    } else {
                        lastError = "Invalid JSON structure in AI output ($model)"
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "Network error"
                    Log.e(TAG, "Error with model $model: ${e.message}", e)
                }
            }

            Log.e(TAG, "All extraction attempts failed. Last error: $lastError")
            val userFriendlyError = when {
                lastError?.contains("API key", ignoreCase = true) == true -> lastError!!
                lastError?.contains("404", ignoreCase = true) == true || lastError?.contains("no longer available", ignoreCase = true) == true ->
                    "AI service unavailable. Please check your Gemini API key."
                else -> lastError ?: "Failed to extract schedule with AI. Please check your connection."
            }
            throw Exception(userFriendlyError)
        }
    }
}
