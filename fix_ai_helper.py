import re

with open("app/src/main/java/com/example/viewmodel/AiHelper.kt", "r") as f:
    content = f.read()

# Fix parameter order
content = content.replace(
"""        rawText: String, 
        image: Bitmap?, 
        fileBytes: ByteArray? = null,
        fileMimeType: String? = null,
        apiKey: String""",
"""        rawText: String, 
        image: Bitmap?, 
        apiKey: String,
        fileBytes: ByteArray? = null,
        fileMimeType: String? = null"""
)

# Replace teacherSchema with full responseSchema
schema_code = """
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

            val responseSchema = Schema(
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
"""
content = re.sub(r'            // Try Schema format.*?            val generativeModel = GenerativeModel\(', schema_code + '\n            val generativeModel = GenerativeModel(', content, flags=re.DOTALL)

# Add responseSchema = responseSchema to generationConfig
content = content.replace("responseMimeType = \"application/json\"", "responseMimeType = \"application/json\"\n                    responseSchema = responseSchema")

with open("app/src/main/java/com/example/viewmodel/AiHelper.kt", "w") as f:
    f.write(content)
