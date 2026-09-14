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
                    val rawDay = schedule.day ?: "Unknown"
                    val day = when (rawDay.trim().lowercase()) {
                        "mon", "monday" -> "Monday"
                        "tue", "tues", "tuesday" -> "Tuesday"
                        "wed", "wednesday" -> "Wednesday"
                        "thu", "thur", "thurs", "thursday" -> "Thursday"
                        "fri", "friday" -> "Friday"
                        "sat", "saturday" -> "Saturday"
                        "sun", "sunday" -> "Sunday"
                        else -> rawDay.trim().replaceFirstChar { it.uppercase() }
                    }
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
    fun getAllCourses() = dao.getAllCourses()
    suspend fun getAllCoursesSync() = dao.getAllCoursesSync()
    fun getScheduleForDay(day: String) = dao.getScheduleForDay(day)
    suspend fun getCourseById(id: String) = dao.getCourseById(id)
    fun getStudentsByCourse(courseId: String) = dao.getStudentsByCourse(courseId)
    suspend fun getStudentsByCourseSync(courseId: String) = dao.getStudentsByCourseSync(courseId)
    suspend fun getScheduleSlotById(id: String) = dao.getScheduleSlotById(id)
    fun getScheduleSlotsForCourse(courseId: String) = dao.getScheduleSlotsForCourse(courseId)
    suspend fun getScheduleSlotsForCourseSync(courseId: String) = dao.getScheduleSlotsForCourseSync(courseId)
    fun getAllScheduleSlots() = dao.getAllScheduleSlots()
    suspend fun getAllScheduleSlotsSync() = dao.getAllScheduleSlotsSync()
    suspend fun saveAttendance(record: AttendanceRecordEntity) {
        withContext(Dispatchers.IO) {
            val existing = dao.getAttendanceRecord(record.date, record.scheduleSlotId, record.studentId)
            val toSave = if (existing != null && record.id == 0) {
                record.copy(id = existing.id)
            } else {
                record
            }
            dao.insertAttendance(toSave)
        }
    }

    suspend fun saveAttendanceBatch(records: List<AttendanceRecordEntity>) {
        withContext(Dispatchers.IO) {
            val toSaveList = records.map { record ->
                val existing = dao.getAttendanceRecord(record.date, record.scheduleSlotId, record.studentId)
                if (existing != null && record.id == 0) record.copy(id = existing.id) else record
            }
            dao.insertAttendanceBatch(toSaveList)
        }
    }

    suspend fun insertStudent(student: StudentEntity) {
        withContext(Dispatchers.IO) { dao.insertStudent(student) }
    }

    suspend fun updateStudent(student: StudentEntity) {
        withContext(Dispatchers.IO) { dao.updateStudent(student) }
    }

    suspend fun deleteStudentById(id: String) {
        withContext(Dispatchers.IO) { dao.deleteStudentById(id) }
    }

    suspend fun getStudentById(id: String): StudentEntity? = withContext(Dispatchers.IO) {
        dao.getStudentById(id)
    }

    fun getStudentCountForCourse(courseId: String) = dao.getStudentCountForCourse(courseId)
    fun searchStudents(courseId: String, query: String) = dao.searchStudents(courseId, query)
    suspend fun getStudentsPaged(courseId: String, limit: Int, offset: Int) = withContext(Dispatchers.IO) {
        dao.getStudentsPaged(courseId, limit, offset)
    }

    fun getAttendanceForSession(date: String, scheduleSlotId: String) = dao.getAttendanceForSession(date, scheduleSlotId)
    suspend fun getAttendanceRecord(date: String, slotId: String, studentId: String) = withContext(Dispatchers.IO) {
        dao.getAttendanceRecord(date, slotId, studentId)
    }
    fun getAttendanceForCourse(courseId: String) = dao.getAttendanceForCourse(courseId)
    suspend fun getAttendanceForCourseSync(courseId: String) = withContext(Dispatchers.IO) {
        dao.getAttendanceForCourseSync(courseId)
    }
    fun getAttendanceForStudent(studentId: String) = dao.getAttendanceForStudent(studentId)
    fun getAttendanceForStudentInCourse(studentId: String, courseId: String) = dao.getAttendanceForStudentInCourse(studentId, courseId)
    fun getAllAttendance() = dao.getAllAttendance()
    suspend fun getAllAttendanceSync() = withContext(Dispatchers.IO) { dao.getAllAttendanceSync() }
    fun getAttendanceCountForCourse(courseId: String) = dao.getAttendanceCountForCourse(courseId)
    fun getDistinctAttendanceDatesForCourse(courseId: String) = dao.getDistinctAttendanceDatesForCourse(courseId)
    fun getAttendanceForDateRange(courseId: String, startDate: String, endDate: String) = dao.getAttendanceForDateRange(courseId, startDate, endDate)
    suspend fun getFirstAttendanceForSession(date: String, slotId: String) = withContext(Dispatchers.IO) {
        dao.getFirstAttendanceForSession(date, slotId)
    }
    suspend fun deleteAttendance(date: String, scheduleSlotId: String, studentId: String) = withContext(Dispatchers.IO) {
        dao.deleteAttendance(date, scheduleSlotId, studentId)
    }
    suspend fun deleteStudentAttendanceForCourseDate(date: String, scheduleSlotId: String, courseId: String, studentId: String = "self") = withContext(Dispatchers.IO) {
        dao.deleteStudentAttendanceForCourseDate(date, scheduleSlotId, courseId, studentId)
    }
    suspend fun deleteAttendanceByCourse(courseId: String) = withContext(Dispatchers.IO) {
        dao.deleteAttendanceByCourse(courseId)
    }
    suspend fun replaceAttendanceForSession(date: String, scheduleSlotId: String, records: List<AttendanceRecordEntity>) = withContext(Dispatchers.IO) {
        dao.replaceAttendanceForSession(date, scheduleSlotId, records)
    }
}
