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
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                        temperature = 0.1f
                    }
                )

                val prompt = """
                    You are an expert data extractor. The user will provide raw text, JSON, or an image of a class timetable.
                    Extract this into a specific JSON schema. Do your best to extract as much information as you can understand.
                    The schema is:
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
                            { "day": "String (e.g., Mon, Tue)", "time": "String (e.g., 9:00 AM - 10:00 AM)", "location": "String" }
                          ],
                          "students": [
                            { "id": "String", "name": "String", "rollNumber": "String" }
                          ]
                        }
                      ]
                    }
                    
                    Return ONLY valid JSON matching this schema exactly. If IDs are missing, generate short unique strings like 'b1', 'b1s1', 't1'.
                    If any field is unknown, leave it empty or guess logically based on context.
                    
                    Raw Text Input:
                    ${rawText.ifBlank { "No text provided" }}
                """.trimIndent()

                val inputContent = content {
                    if (image != null) {
                        image(image)
                    }
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)
                response.text
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
