package com.example.data
import com.example.models.UploadData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class Repository(val dao: AppDao) {
    suspend fun processUploadData(data: UploadData) {
        withContext(Dispatchers.IO) {
            dao.wipeAllData()
            val courses = data.courses.map { CourseEntity(it.id, it.name, it.code, it.credits) }
            dao.insertCourses(courses)
            val students = data.courses.flatMap { course ->
                course.students.map { student -> StudentEntity(student.id, student.name, student.rollNumber, course.id) }
            }
            dao.insertStudents(students)
            val slots = data.weeklySchedule.map { slot ->
                ScheduleSlotEntity(slot.id, slot.courseId, slot.dayOfWeek, slot.startTime, slot.endTime, slot.room, slot.section ?: "")
            }
            dao.insertScheduleSlots(slots)
        }
    }
    suspend fun processTimetableImport(data: com.example.data.ImportTimetableData) {
        withContext(Dispatchers.IO) {
            val newCourses = mutableListOf<CourseEntity>()
            val newStudents = mutableListOf<StudentEntity>()
            val newSlots = mutableListOf<ScheduleSlotEntity>()
            data.batches?.forEach { batch ->
                val courseId = batch.batchId?.takeIf { it.isNotBlank() } ?: "batch_${java.util.UUID.randomUUID()}"
                val courseCode = batch.course?.code?.takeIf { it.isNotBlank() } ?: "Unknown Code"
                val courseName = batch.course?.name?.takeIf { it.isNotBlank() } ?: "Unknown Course"
                val section = batch.section ?: ""
                val defaultLocation = batch.location ?: ""
                newCourses.add(CourseEntity(courseId, courseName, courseCode, 0))
                batch.students?.forEach { student ->
                    val studentId = student.id?.takeIf { it.isNotBlank() } ?: "student_${java.util.UUID.randomUUID()}"
                    newStudents.add(StudentEntity(studentId, student.name ?: "Unknown", student.rollNumber ?: "", courseId))
                }
                batch.weeklySchedule?.forEach { schedule ->
                    val slotId = "slot_${java.util.UUID.randomUUID()}"
                    val day = schedule.day ?: "Unknown"
                    val timeString = schedule.time ?: ""
                    val loc = schedule.location?.takeIf { it.isNotBlank() } ?: defaultLocation
                    val parts = timeString.split("-").map { it.trim() }
                    val start = parts.getOrNull(0) ?: timeString
                    val end = parts.getOrNull(1) ?: ""
                    newSlots.add(ScheduleSlotEntity(id = slotId, courseId = courseId, dayOfWeek = day, startTime = start, endTime = end, room = loc, section = section))
                }
            }
            dao.insertCourses(newCourses)
            dao.insertStudents(newStudents)
            dao.insertScheduleSlots(newSlots)
        }
    }
    suspend fun wipeAllData() { withContext(Dispatchers.IO) { dao.wipeAllData() } }
    fun getScheduleForDay(day: String) = dao.getScheduleForDay(day)
    suspend fun getCourseById(id: String) = dao.getCourseById(id)
    fun getStudentsByCourse(courseId: String) = dao.getStudentsByCourse(courseId)
    suspend fun getStudentsByCourseSync(courseId: String) = dao.getStudentsByCourseSync(courseId)
    suspend fun getScheduleSlotById(id: String) = dao.getScheduleSlotById(id)
    fun getScheduleSlotsForCourse(courseId: String) = dao.getScheduleSlotsForCourse(courseId)
    suspend fun getScheduleSlotsForCourseSync(courseId: String) = dao.getScheduleSlotsForCourseSync(courseId)
    suspend fun saveAttendance(record: AttendanceRecordEntity) = dao.insertAttendance(record)
    fun getAttendanceForSession(date: String, scheduleSlotId: String) = dao.getAttendanceForSession(date, scheduleSlotId)
    suspend fun getAttendanceRecord(date: String, slotId: String, studentId: String) = dao.getAttendanceRecord(date, slotId, studentId)
    fun getAttendanceForCourse(courseId: String) = dao.getAttendanceForCourse(courseId)
    suspend fun deleteAttendance(date: String, scheduleSlotId: String, studentId: String) = dao.deleteAttendance(date, scheduleSlotId, studentId)
}
