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
    @Query("SELECT * FROM courses")
    suspend fun getAllCoursesSync(): List<CourseEntity>
    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getCourseById(id: String): CourseEntity?
    @Query("SELECT * FROM students WHERE courseId = :courseId ORDER BY rollNumber ASC")
    fun getStudentsByCourse(courseId: String): Flow<List<StudentEntity>>
    @Query("SELECT * FROM students WHERE courseId = :courseId ORDER BY rollNumber ASC")
    suspend fun getStudentsByCourseSync(courseId: String): List<StudentEntity>
    @Query("SELECT * FROM schedule_slots WHERE dayOfWeek = :dayOfWeek OR dayOfWeek = substr(:dayOfWeek, 1, 3) OR lower(dayOfWeek) = lower(:dayOfWeek) ORDER BY startTime ASC")
    fun getScheduleForDay(dayOfWeek: String): Flow<List<ScheduleSlotEntity>>
    @Query("SELECT * FROM schedule_slots WHERE id = :id LIMIT 1")
    suspend fun getScheduleSlotById(id: String): ScheduleSlotEntity?
    @Query("SELECT * FROM schedule_slots WHERE courseId = :courseId ORDER BY dayOfWeek, startTime ASC")
    fun getScheduleSlotsForCourse(courseId: String): Flow<List<ScheduleSlotEntity>>

    @Query("SELECT * FROM schedule_slots")
    fun getAllScheduleSlots(): Flow<List<ScheduleSlotEntity>>

    @Query("SELECT * FROM schedule_slots")
    suspend fun getAllScheduleSlotsSync(): List<ScheduleSlotEntity>

    @Query("SELECT * FROM schedule_slots WHERE courseId = :courseId")
    suspend fun getScheduleSlotsForCourseSync(courseId: String): List<ScheduleSlotEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceRecordEntity)
    @Query("SELECT * FROM attendance WHERE date = :date AND scheduleSlotId = :scheduleSlotId")
    fun getAttendanceForSession(date: String, scheduleSlotId: String): Flow<List<AttendanceRecordEntity>>
    @Query("SELECT * FROM attendance WHERE date = :date AND scheduleSlotId = :scheduleSlotId AND studentId = :studentId LIMIT 1")
    suspend fun getAttendanceRecord(date: String, scheduleSlotId: String, studentId: String): AttendanceRecordEntity?
    
    @Query("SELECT attendance.* FROM attendance INNER JOIN schedule_slots ON attendance.scheduleSlotId = schedule_slots.id WHERE schedule_slots.courseId = :courseId")
    fun getAttendanceForCourse(courseId: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance")
    fun getAllAttendance(): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance WHERE date = :date AND scheduleSlotId = :scheduleSlotId LIMIT 1")
    suspend fun getFirstAttendanceForSession(date: String, scheduleSlotId: String): AttendanceRecordEntity?

    @Query("DELETE FROM attendance WHERE date = :date AND scheduleSlotId = :scheduleSlotId AND studentId = :studentId")
    suspend fun deleteAttendance(date: String, scheduleSlotId: String, studentId: String)

    // Sync Engine Queries
    @Query("SELECT * FROM attendance WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingAttendanceSync(): List<AttendanceRecordEntity>

    @Query("SELECT COUNT(*) FROM attendance WHERE syncStatus != 'SYNCED'")
    fun getPendingAttendanceCount(): Flow<Int>

    @Query("UPDATE attendance SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAttendanceSyncStatus(id: Int, status: String, updatedAt: Long = System.currentTimeMillis())

    // User Profile Queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM users_profile WHERE authId = :authId OR id = :authId LIMIT 1")
    suspend fun getUserProfileByAuthId(authId: String): UserProfileEntity?

    @Query("SELECT * FROM users_profile WHERE authId = :authId OR id = :authId LIMIT 1")
    fun getUserProfileFlow(authId: String): Flow<UserProfileEntity?>

    // Attendance Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceSession(session: AttendanceSessionEntity)

    @Query("SELECT * FROM attendance_sessions WHERE courseId = :courseId ORDER BY date DESC")
    fun getSessionsForCourse(courseId: String): Flow<List<AttendanceSessionEntity>>

    @Query("SELECT * FROM attendance_sessions WHERE teacherId = :teacherId AND isActive = 1")
    fun getActiveSessionsForTeacher(teacherId: String): Flow<List<AttendanceSessionEntity>>

    // Enrollments
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnrollments(enrollments: List<EnrollmentEntity>)

    @Query("SELECT * FROM enrollments WHERE studentId = :studentId")
    fun getEnrollmentsForStudent(studentId: String): Flow<List<EnrollmentEntity>>

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourseById(courseId: String)

    @Query("DELETE FROM students WHERE courseId = :courseId")
    suspend fun deleteStudentsByCourseId(courseId: String)

    @Query("DELETE FROM schedule_slots WHERE courseId = :courseId")
    suspend fun deleteScheduleSlotsByCourseId(courseId: String)

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
