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
                        text("You are an expert data extraction AI for a university attendance system. Your task is to intelligently map messy, unstructured data (text, images, or bad JSON) into a strict internal JSON schema. You are forgiving of typos, excellent at inferring context, and strict about outputting valid JSON.") 
                    },
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                        temperature = 0.1f
                    }
                )

                val prompt = """
                    Analyze the following input (which may include text and/or an image) representing a teacher's schedule, timetable, or student list.
                    
                    Your goal is to extract this information and map it EXACTLY to the following JSON schema. 
                    
                    # Intelligent Mapping Rules:
                    1. **Teacher Context**: Try to identify the teacher's name. If missing, use "Unknown Faculty".
                    2. **Batches & Courses**: A "batch" groups a Course, its Schedule, and its Students. If multiple schedules belong to the same course/section, group them in one batch.
                    3. **Course Names**: If you see subjects like "Math", "CS101", put them in course.name and course.code. If omitted, invent a logical placeholder like "Imported Course".
                    4. **Schedule Normalization**: Standardize days to 3-letter formats (Mon, Tue, Wed, Thu, Fri, Sat, Sun). Clean up times to "HH:MM AM/PM - HH:MM AM/PM".
                    5. **Student Lists**: If you see lines of names/numbers, they are students. Map names to 'name', and IDs/numbers to 'rollNumber'. 
                    6. **Missing IDs**: Always generate clean, unique IDs for missing fields (e.g., 'batch_1', 'stu_123').
                    7. **Partial Data**: If the user provides ONLY a schedule (no students), or ONLY students (no schedule), still return valid JSON wrapping it in a generic batch so the system can accept it.
                    
                    # Target JSON Schema:
                    {
                      "teacher": { "name": "String", "id": "String" },
                      "batches": [
                        {
                          "batchId": "String",
                          "year": "String (e.g. 2024)",
                          "semester": "String (e.g. 1st Sem)",
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
                    
                    Return ONLY a raw, valid JSON object. Do not wrap it in markdown block quotes (```json ... ```). Just the raw braces.
                    
                    Raw Input:
                    ${rawText.ifBlank { "No text provided" }}
                """.trimIndent()

                val inputContent = content {
                    if (image != null) {
                        image(image)
                    }
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)
                response.text?.replace("```json", "")?.replace("```", "")?.trim()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
