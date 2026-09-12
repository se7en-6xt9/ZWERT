package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiHelper {
    private const val TAG = "AiHelper"

    suspend fun parseTimetableData(
        rawText: String, 
        image: Bitmap?, 
        apiKey: String,
        fileBytes: ByteArray? = null,
        fileMimeType: String? = null
    ): String? {
        return withContext(Dispatchers.IO) {
            val systemPrompt = """
                You are a timetable data extraction engine. You will be given a 
                teacher's class schedule as an image, text, or document. Extract the 
                information and map it to the required JSON schema.

                Rules:
                1. Every unique combination of year + semester + section + course is 
                   ONE batch entry — do not create duplicate batches for the same 
                   combination; merge their schedule entries together instead.
                2. For each batch, add one entry to "weeklySchedule" for every time 
                   it occurs in a week. Classes can be ANY duration (from 15 minutes 
                   to several hours) and occur at ANY time of day — always extract 
                   the actual times shown, never assume a standard/fixed class length.
                3. If a student roster/list is visible in the source, extract each 
                   student once into that batch's "students" array. If no student 
                   list is present, return an empty array for "students" — never 
                   invent names or roll numbers.
                4. For any piece of information that is not present in the source, or 
                   that you are not reasonably confident about, output null for that 
                   field. Do not guess, estimate, or hallucinate values to fill gaps — 
                   an incomplete but accurate result is far better than a complete but 
                   inaccurate one.
                5. Ignore any information in the source that does not correspond to a 
                   field in the schema (e.g. credit-hour tables, coordinator names, 
                   footnotes, letterheads) — do not invent extra fields, and do not 
                   let irrelevant text cause an error; simply skip it.
                6. If the source is unclear, low quality, or only partially readable, 
                   extract whatever you CAN confidently read, set everything else to 
                   null, and still return a valid result — never refuse to respond or 
                   return an empty/error response just because part of the input is 
                   unclear.
            """.trimIndent()
            

            val studentSchema = Schema(
                name = "student",
                description = "Student info",
                type = com.google.ai.client.generativeai.type.FunctionType.OBJECT,
                properties = mapOf(
                    "id" to Schema(name="id", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING),
                    "name" to Schema(name="name", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING),
                    "rollNumber" to Schema(name="rollNumber", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = true)
                )
            )

            val scheduleSchema = Schema(
                name = "schedule",
                description = "Class schedule",
                type = com.google.ai.client.generativeai.type.FunctionType.OBJECT,
                properties = mapOf(
                    "day" to Schema(name="day", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING),
                    "time" to Schema(name="time", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = true),
                    "location" to Schema(name="location", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = true)
                )
            )

            val courseSchema = Schema(
                name = "course",
                description = "Course details",
                type = com.google.ai.client.generativeai.type.FunctionType.OBJECT,
                properties = mapOf(
                    "code" to Schema(name="code", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = true),
                    "name" to Schema(name="name", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = true)
                ),
                nullable = true
            )

            val batchSchema = Schema(
                name = "batch",
                description = "Batch details",
                type = com.google.ai.client.generativeai.type.FunctionType.OBJECT,
                properties = mapOf(
                    "batchId" to Schema(name="batchId", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING),
                    "year" to Schema(name="year", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = true),
                    "semester" to Schema(name="semester", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = true),
                    "course" to courseSchema,
                    "section" to Schema(name="section", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = true),
                    "location" to Schema(name="location", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = true),
                    "weeklySchedule" to Schema(name="weeklySchedule", description="", type = com.google.ai.client.generativeai.type.FunctionType.ARRAY, items = scheduleSchema, nullable = true),
                    "students" to Schema(name="students", description="", type = com.google.ai.client.generativeai.type.FunctionType.ARRAY, items = studentSchema, nullable = true)
                )
            )

            val myResponseSchema = Schema(
                name = "root",
                description = "Root response",
                type = com.google.ai.client.generativeai.type.FunctionType.OBJECT,
                properties = mapOf(
                    "teacher" to Schema(
                        name = "teacher",
                        description = "Teacher details",
                        type = com.google.ai.client.generativeai.type.FunctionType.OBJECT,
                        properties = mapOf(
                            "name" to Schema(name="name", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = true),
                            "id" to Schema(name="id", description="", type = com.google.ai.client.generativeai.type.FunctionType.STRING, nullable = true)
                        ),
                        nullable = true
                    ),
                    "batches" to Schema(name="batches", description="", type = com.google.ai.client.generativeai.type.FunctionType.ARRAY, items = batchSchema)
                )
            )

            val generativeModel = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey,
                systemInstruction = content { text(systemPrompt) },
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = myResponseSchema
                    temperature = 0.1f
                }
            )

            var attempt = 0
            var maxAttempts = 2
            var lastError: String? = null
            var currentPromptText = rawText.ifBlank { "Extract the timetable from this input." }

            while (attempt < maxAttempts) {
                try {
                    Log.d(TAG, "Starting extraction attempt ${attempt + 1}")
                    val inputContent = content {
                        if (image != null && attempt == 0) {
                            image(image)
                        }
                        if (fileBytes != null && fileMimeType != null && attempt == 0) {
                            blob(fileMimeType, fileBytes)
                        }
                        text(currentPromptText)
                    }

                    val response = generativeModel.generateContent(inputContent)
                    val finishReason = response.candidates.firstOrNull()?.finishReason
                    Log.d(TAG, "Raw response received. Finish Reason: $finishReason")
                    
                    if (finishReason?.name != "STOP") {
                        Log.e(TAG, "Warning: Finish reason is not STOP (it is $finishReason). Data might be incomplete or blocked.")
                    }

                    var rawJson = response.text ?: ""
                    Log.d(TAG, "Raw JSON Output length: ${rawJson.length}. Content snippet: ${rawJson.take(100)}")

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
                        throw Exception("Output is not valid JSON objects. Raw output: $rawJson")
                    }

                } catch (e: Exception) {
                    lastError = e.message
                    Log.e(TAG, "Extraction failed on attempt ${attempt + 1}: ${e.message}", e)
                    attempt++
                    currentPromptText = "Your previous output did not match the required schema. Return ONLY valid JSON exactly matching the provided schema. Previous error: ${e.message}"
                }
            }
            Log.e(TAG, "All extraction attempts failed. Last error: $lastError")
            throw Exception("Failed after $maxAttempts attempts. Last error: $lastError")
        }
    }
}
