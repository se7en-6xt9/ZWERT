import re
with open("app/src/main/java/com/example/data/Daos.kt", "r") as f:
    content = f.read()

content = content.replace(
    "@Query(\"SELECT * FROM schedule_slots WHERE courseId = :courseId\")",
    "@Query(\"SELECT * FROM schedule_slots\")\n    suspend fun getAllScheduleSlotsSync(): List<ScheduleSlotEntity>\n\n    @Query(\"SELECT * FROM schedule_slots WHERE courseId = :courseId\")"
)
with open("app/src/main/java/com/example/data/Daos.kt", "w") as f:
    f.write(content)
