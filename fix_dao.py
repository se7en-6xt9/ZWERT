with open("app/src/main/java/com/example/data/Daos.kt", "r") as f:
    content = f.read()

import re

# Insert getScheduleSlotsForCourseSync after getScheduleSlotsForCourse
content = re.sub(r'    fun getScheduleSlotsForCourse\(courseId: String\): kotlinx.coroutines.flow.Flow<List<ScheduleSlotEntity>>', r'    fun getScheduleSlotsForCourse(courseId: String): kotlinx.coroutines.flow.Flow<List<ScheduleSlotEntity>>\n\n    @Query("SELECT * FROM schedule_slots WHERE courseId = :courseId")\n    suspend fun getScheduleSlotsForCourseSync(courseId: String): List<ScheduleSlotEntity>', content)

with open("app/src/main/java/com/example/data/Daos.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/com/example/data/Repository.kt", "r") as f:
    content = f.read()

content = re.sub(r'    fun getScheduleSlotsForCourse\(courseId: String\) = dao.getScheduleSlotsForCourse\(courseId\)', r'    fun getScheduleSlotsForCourse(courseId: String) = dao.getScheduleSlotsForCourse(courseId)\n    suspend fun getScheduleSlotsForCourseSync(courseId: String) = dao.getScheduleSlotsForCourseSync(courseId)', content)

with open("app/src/main/java/com/example/data/Repository.kt", "w") as f:
    f.write(content)
