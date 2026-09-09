package com.example.viewmodel

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiHelper {
    suspend fun parseTimetableData(rawText: String, image: Bitmap?, apiKey: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-pro",
                    apiKey = apiKey,
                    systemInstruction = content { 
                        text("""
                            You are an advanced Data Extraction & OCR AI for an academic timetable app. 
                            Your only job is to consume messy data (text, malformed JSON, or images) and output perfectly formatted, strict JSON matching our exact schema. 
                            If the user provides a broken JSON, fix it. If the user provides a picture, use OCR to understand it. 
                            If data is missing, make intelligent guesses or fill with sensible placeholders (e.g. 'Unknown Course', 'TBD', or generate unique IDs). 
                            NEVER fail to output the JSON structure.
                        """.trimIndent()) 
                    },
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                        temperature = 0.2f
                    }
                )

                val prompt = """
                    Analyze the following input. It might be a messy text snippet, a broken JSON file, or an uploaded image (OCR required).
                    
                    Your goal is to extract whatever information is available and map it to the STRICT JSON schema below.
                    
                    # Core Rules & Edge Cases:
                    1. **Broken JSON Handling**: If the text provided looks like a malformed JSON file (missing quotes, trailing commas, missing brackets), FIX it and map it to the requested schema.
                    2. **OCR & Image Extraction**: If an image is provided, thoroughly scan it. Time grids become 'weeklySchedule', lists of names become 'students'.
                    3. **Partial Data Recovery**: 
                       - If you only find a list of students, wrap them inside a single batch with a dummy course ("Imported Course").
                       - If you only find a timetable, wrap it inside a batch with an empty students list.
                       - We MUST return at least one batch if any data is found.
                    4. **Never Omit Fields**: Even if a field is unknown, provide it with an empty string "", or a sensible default.
                    5. **Auto-Generate IDs**: Any missing 'id', 'batchId', 'rollNumber' MUST be auto-generated (e.g. "batch_001", "stu_001").
                    6. **Schedule Formatting**: Standardize days to 3 letters (Mon, Tue, Wed, Thu, Fri, Sat, Sun). Clean up times to "9:00 AM - 10:00 AM".

                    # Target Strict Schema:
                    {
                      "teacher": { "name": "String", "id": "String" },
                      "batches": [
                        {
                          "batchId": "String",
                          "year": "String",
                          "semester": "String",
                          "course": { "code": "String", "name": "String" },
                          "section": "String",
                          "location": "String",
                          "weeklySchedule": [
                            { "day": "String", "time": "String", "location": "String" }
                          ],
                          "students": [
                            { "id": "String", "name": "String", "rollNumber": "String" }
                          ]
                        }
                      ]
                    }
                    
                    Output ONLY valid JSON. No markdown blocks, no conversational text.
                    
                    Raw Input:
                    ${rawText.ifBlank { "No text provided, rely on image if present." }}
                """.trimIndent()

                val inputContent = content {
                    if (image != null) {
                        image(image)
                    }
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)
                
                // Cleanup: Extract JSON in case Gemini wraps it in markdown despite instructions
                var rawJson = response.text ?: ""
                if (rawJson.contains("```json")) {
                    rawJson = rawJson.substringAfter("```json").substringBeforeLast("```")
                } else if (rawJson.contains("```")) {
                    rawJson = rawJson.substringAfter("```").substringBeforeLast("```")
                }
                
                rawJson.trim()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
