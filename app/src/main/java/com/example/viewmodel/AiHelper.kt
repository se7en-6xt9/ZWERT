package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiHelper {
    private const val TAG = "AiHelper"

    suspend fun parseTimetableData(rawText: String, image: Bitmap?, apiKey: String): String? {
        return withContext(Dispatchers.IO) {
            val systemPrompt = """
                You are a timetable data extraction engine. You will be given a 
                teacher's timetable (as an image, text, or document). Extract the 
                schedule and return ONLY valid JSON matching this exact schema — no 
                markdown formatting, no code fences, no explanation, no extra text 
                before or after the JSON.

                Rules:
                1. Every unique combination of year + semester + section + course is 
                   one "batch" — group accordingly, don't create duplicate batches 
                   for the same combination.
                2. For each batch, list EVERY weekly occurrence in "weeklySchedule" 
                   — a class can be any duration (30 minutes to 8+ hours) and any 
                   time of day (e.g. "6:00 AM - 7:00 AM", "7:00 PM - 9:00 PM") — do 
                   not assume standard class lengths or standard hours, extract the 
                   ACTUAL times shown.
                3. If the timetable includes a student roster, list each student 
                   once under that batch's "students" array. If no student list is 
                   present in the source, return an empty array — do not invent 
                   names.
                4. For ANY field where the information is not present, ambiguous, or 
                   you are not confident, use JSON null — NEVER guess, invent, or 
                   hallucinate a value to fill a gap.
                5. Ignore any information in the source that doesn't map to a field 
                   in this schema (e.g. credit hours, faculty designation notes, 
                   footer text) — do not add extra keys not defined in the schema.
                6. Return ONLY the JSON object. No ```json fences, no commentary.

                Schema:
                {
                  "teacher": { "name": "String|null", "id": "String|null" },
                  "batches": [
                    {
                      "batchId": "String",
                      "year": "String|null",
                      "semester": "String|null",
                      "course": { "code": "String|null", "name": "String|null" },
                      "section": "String|null",
                      "location": "String|null",
                      "weeklySchedule": [ { "day": "String", "time": "String|null", "location": "String|null" } ],
                      "students": [ { "id": "String", "name": "String", "rollNumber": "String|null" } ]
                    }
                  ]
                }
            """.trimIndent()

            val generativeModel = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey,
                systemInstruction = content { text(systemPrompt) },
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    temperature = 0.1f
                }
            )

            var attempt = 0
            var maxAttempts = 2
            var lastError: String? = null
            var currentPromptText = rawText.ifBlank { "Extract the timetable from the image." }

            while (attempt < maxAttempts) {
                try {
                    Log.d(TAG, "Starting extraction attempt ${attempt + 1}")
                    val inputContent = content {
                        if (image != null && attempt == 0) {
                            image(image)
                        }
                        text(currentPromptText)
                    }

                    val response = generativeModel.generateContent(inputContent)
                    Log.d(TAG, "Raw response received. Finish Reason: ${response.candidates.firstOrNull()?.finishReason}")
                    
                    var rawJson = response.text ?: ""
                    Log.d(TAG, "Raw JSON Output length: ${rawJson.length}")

                    if (rawJson.contains("```json")) {
                        rawJson = rawJson.substringAfter("```json").substringBeforeLast("```")
                    } else if (rawJson.contains("```")) {
                        rawJson = rawJson.substringAfter("```").substringBeforeLast("```")
                    }
                    
                    rawJson = rawJson.trim()
                    val startIndex = rawJson.indexOf('{')
                    val endIndex = rawJson.lastIndexOf('}')
                    if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
                        rawJson = rawJson.substring(startIndex, endIndex + 1)
                    }

                    // Quick validation to see if it's parsable JSON
                    if (rawJson.startsWith("{") && rawJson.endsWith("}")) {
                        return@withContext rawJson
                    } else {
                        throw Exception("Output is not valid JSON objects.")
                    }

                } catch (e: Exception) {
                    lastError = e.message
                    Log.e(TAG, "Extraction failed on attempt ${attempt + 1}: ${e.message}", e)
                    attempt++
                    currentPromptText = "Your previous response was not valid JSON. Return ONLY the corrected valid JSON matching the schema, nothing else. Previous output failed with: ${e.message}"
                }
            }
            Log.e(TAG, "All extraction attempts failed. Last error: $lastError")
            null
        }
    }
}
