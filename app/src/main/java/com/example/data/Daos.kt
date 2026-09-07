package com.example.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<StudentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleSlots(slots: List<ScheduleSlotEntity>)
    @Query("SELECT * FROM courses")
    fun getAllCourses(): Flow<List<CourseEntity>>
    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getCourseById(id: String): CourseEntity?
    @Query("SELECT * FROM students WHERE courseId = :courseId ORDER BY rollNumber ASC")
    fun getStudentsByCourse(courseId: String): Flow<List<StudentEntity>>
    @Query("SELECT * FROM students WHERE courseId = :courseId ORDER BY rollNumber ASC")
    suspend fun getStudentsByCourseSync(courseId: String): List<StudentEntity>
    @Query("SELECT * FROM schedule_slots WHERE dayOfWeek = :dayOfWeek ORDER BY startTime ASC")
    fun getScheduleForDay(dayOfWeek: String): Flow<List<ScheduleSlotEntity>>
    @Query("SELECT * FROM schedule_slots WHERE id = :id LIMIT 1")
    suspend fun getScheduleSlotById(id: String): ScheduleSlotEntity?
    @Query("SELECT * FROM schedule_slots WHERE courseId = :courseId ORDER BY dayOfWeek, startTime ASC")
    fun getScheduleSlotsForCourse(courseId: String): Flow<List<ScheduleSlotEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceRecordEntity)
    @Query("SELECT * FROM attendance WHERE date = :date AND scheduleSlotId = :scheduleSlotId")
    fun getAttendanceForSession(date: String, scheduleSlotId: String): Flow<List<AttendanceRecordEntity>>
    @Query("SELECT * FROM attendance WHERE date = :date AND scheduleSlotId = :scheduleSlotId AND studentId = :studentId LIMIT 1")
    suspend fun getAttendanceRecord(date: String, scheduleSlotId: String, studentId: String): AttendanceRecordEntity?
    
    @Query("SELECT attendance.* FROM attendance INNER JOIN schedule_slots ON attendance.scheduleSlotId = schedule_slots.id WHERE schedule_slots.courseId = :courseId")
    fun getAttendanceForCourse(courseId: String): Flow<List<AttendanceRecordEntity>>

    @Query("DELETE FROM attendance WHERE date = :date AND scheduleSlotId = :scheduleSlotId AND studentId = :studentId")
    suspend fun deleteAttendance(date: String, scheduleSlotId: String, studentId: String)

    @Query("DELETE FROM courses")
    suspend fun wipeCourses()
    @Query("DELETE FROM students")
    suspend fun wipeStudents()
    @Query("DELETE FROM schedule_slots")
    suspend fun wipeScheduleSlots()
    @Query("DELETE FROM attendance")
    suspend fun wipeAttendance()
    @Transaction
    suspend fun wipeAllData() {
        wipeCourses()
        wipeStudents()
        wipeScheduleSlots()
        wipeAttendance()
    }
}
