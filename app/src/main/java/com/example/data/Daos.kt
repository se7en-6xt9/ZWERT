package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // ==========================================
    // Course Operations
    // ==========================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Update
    suspend fun updateCourse(course: CourseEntity)

    @Delete
    suspend fun deleteCourse(course: CourseEntity)

    @Query("SELECT * FROM courses")
    fun getAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses")
    suspend fun getAllCoursesSync(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getCourseById(id: String): CourseEntity?

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourseById(courseId: String)

    // ==========================================
    // Student Operations (CRUD & Queries)
    // ==========================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<StudentEntity>)

    @Update
    suspend fun updateStudent(student: StudentEntity)

    @Delete
    suspend fun deleteStudent(student: StudentEntity)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteStudentById(id: String)

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: String): StudentEntity?

    @Query("SELECT * FROM students WHERE courseId = :courseId ORDER BY rollNumber ASC")
    fun getStudentsByCourse(courseId: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE courseId = :courseId ORDER BY rollNumber ASC")
    suspend fun getStudentsByCourseSync(courseId: String): List<StudentEntity>

    @Query("SELECT COUNT(*) FROM students WHERE courseId = :courseId")
    fun getStudentCountForCourse(courseId: String): Flow<Int>

    @Query("SELECT * FROM students WHERE courseId = :courseId AND (name LIKE '%' || :query || '%' OR rollNumber LIKE '%' || :query || '%') ORDER BY rollNumber ASC")
    fun searchStudents(courseId: String, query: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE courseId = :courseId ORDER BY rollNumber ASC LIMIT :limit OFFSET :offset")
    suspend fun getStudentsPaged(courseId: String, limit: Int, offset: Int): List<StudentEntity>

    @Query("DELETE FROM students WHERE courseId = :courseId")
    suspend fun deleteStudentsByCourseId(courseId: String)

    @Query("SELECT * FROM students WHERE LOWER(TRIM(email)) = LOWER(TRIM(:email))")
    suspend fun getStudentsByEmail(email: String): List<StudentEntity>

    @Query("SELECT * FROM students")
    suspend fun getAllStudentsSync(): List<StudentEntity>

    // ==========================================
    // Schedule Slot Operations
    // ==========================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleSlot(slot: ScheduleSlotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleSlots(slots: List<ScheduleSlotEntity>)

    @Update
    suspend fun updateScheduleSlot(slot: ScheduleSlotEntity)

    @Delete
    suspend fun deleteScheduleSlot(slot: ScheduleSlotEntity)

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

    @Query("DELETE FROM schedule_slots WHERE courseId = :courseId")
    suspend fun deleteScheduleSlotsByCourseId(courseId: String)

    // ==========================================
    // Attendance Operations (High-Performance CRUD & Stats)
    // ==========================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceBatch(records: List<AttendanceRecordEntity>)

    @Update
    suspend fun updateAttendance(record: AttendanceRecordEntity)

    @Delete
    suspend fun deleteAttendanceRecord(record: AttendanceRecordEntity)

    @Query("DELETE FROM attendance WHERE date = :date AND scheduleSlotId = :scheduleSlotId AND studentId = :studentId")
    suspend fun deleteAttendance(date: String, scheduleSlotId: String, studentId: String)

    @Query("DELETE FROM attendance WHERE date = :date AND studentId = :studentId AND (courseId = :courseId OR scheduleSlotId = :scheduleSlotId OR scheduleSlotId = 'slot_' || :courseId OR scheduleSlotId = 'slot_self_' || :courseId)")
    suspend fun deleteStudentAttendanceForCourseDate(date: String, scheduleSlotId: String, courseId: String, studentId: String = "self")

    @Query("DELETE FROM attendance WHERE courseId = :courseId")
    suspend fun deleteAttendanceByCourse(courseId: String)

    @Query("SELECT * FROM attendance WHERE date = :date AND scheduleSlotId = :scheduleSlotId")
    fun getAttendanceForSession(date: String, scheduleSlotId: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance WHERE date = :date AND scheduleSlotId = :scheduleSlotId")
    suspend fun getAttendanceForSessionSync(date: String, scheduleSlotId: String): List<AttendanceRecordEntity>

    @Query("SELECT * FROM attendance WHERE date = :date AND scheduleSlotId = :scheduleSlotId AND studentId = :studentId LIMIT 1")
    suspend fun getAttendanceRecord(date: String, scheduleSlotId: String, studentId: String): AttendanceRecordEntity?

    @Query("SELECT * FROM attendance WHERE courseId = :courseId OR scheduleSlotId IN (SELECT id FROM schedule_slots WHERE courseId = :courseId)")
    fun getAttendanceForCourse(courseId: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance WHERE courseId = :courseId OR scheduleSlotId IN (SELECT id FROM schedule_slots WHERE courseId = :courseId)")
    suspend fun getAttendanceForCourseSync(courseId: String): List<AttendanceRecordEntity>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY date DESC")
    fun getAttendanceForStudent(studentId: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY date DESC")
    suspend fun getAttendanceForStudentSync(studentId: String): List<AttendanceRecordEntity>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId AND (courseId = :courseId OR scheduleSlotId IN (SELECT id FROM schedule_slots WHERE courseId = :courseId)) ORDER BY date DESC")
    fun getAttendanceForStudentInCourse(studentId: String, courseId: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance")
    fun getAllAttendance(): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance")
    suspend fun getAllAttendanceSync(): List<AttendanceRecordEntity>

    @Query("SELECT COUNT(*) FROM attendance WHERE courseId = :courseId")
    fun getAttendanceCountForCourse(courseId: String): Flow<Int>

    @Query("SELECT DISTINCT date FROM attendance WHERE courseId = :courseId OR scheduleSlotId IN (SELECT id FROM schedule_slots WHERE courseId = :courseId) ORDER BY date DESC")
    fun getDistinctAttendanceDatesForCourse(courseId: String): Flow<List<String>>

    @Query("SELECT * FROM attendance WHERE (courseId = :courseId OR scheduleSlotId IN (SELECT id FROM schedule_slots WHERE courseId = :courseId)) AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getAttendanceForDateRange(courseId: String, startDate: String, endDate: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance WHERE date = :date AND scheduleSlotId = :scheduleSlotId LIMIT 1")
    suspend fun getFirstAttendanceForSession(date: String, scheduleSlotId: String): AttendanceRecordEntity?

    @Transaction
    suspend fun replaceAttendanceForSession(date: String, scheduleSlotId: String, records: List<AttendanceRecordEntity>) {
        records.forEach { record ->
            val existing = getAttendanceRecord(date, scheduleSlotId, record.studentId)
            val toSave = if (existing != null && record.id == 0) record.copy(id = existing.id) else record
            insertAttendance(toSave)
        }
    }

    // ==========================================
    // Offline-First Sync Engine Queries
    // ==========================================
    @Query("SELECT * FROM attendance WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingAttendanceSync(): List<AttendanceRecordEntity>

    @Query("SELECT COUNT(*) FROM attendance WHERE syncStatus != 'SYNCED'")
    fun getPendingAttendanceCount(): Flow<Int>

    @Query("UPDATE attendance SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAttendanceSyncStatus(id: Int, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE attendance SET syncStatus = :status, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateAttendanceBatchSyncStatus(ids: List<Int>, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM students WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingStudentsSync(): List<StudentEntity>

    // ==========================================
    // User Profile Queries
    // ==========================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM users_profile WHERE authId = :authId OR id = :authId LIMIT 1")
    suspend fun getUserProfileByAuthId(authId: String): UserProfileEntity?

    @Query("SELECT * FROM users_profile WHERE authId = :authId OR id = :authId LIMIT 1")
    fun getUserProfileFlow(authId: String): Flow<UserProfileEntity?>

    // ==========================================
    // Attendance Sessions & Enrollments
    // ==========================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceSession(session: AttendanceSessionEntity)

    @Query("SELECT * FROM attendance_sessions WHERE courseId = :courseId ORDER BY date DESC")
    fun getSessionsForCourse(courseId: String): Flow<List<AttendanceSessionEntity>>

    @Query("SELECT * FROM attendance_sessions WHERE teacherId = :teacherId AND isActive = 1")
    fun getActiveSessionsForTeacher(teacherId: String): Flow<List<AttendanceSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnrollments(enrollments: List<EnrollmentEntity>)

    @Query("SELECT * FROM enrollments WHERE studentId = :studentId")
    fun getEnrollmentsForStudent(studentId: String): Flow<List<EnrollmentEntity>>

    // ==========================================
    // Official Classes (ERP Feed from Teachers)
    // ==========================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfficialClasses(classes: List<OfficialClassEntity>)

    @Query("SELECT * FROM official_classes WHERE isHidden = 0")
    fun getActiveOfficialClasses(): Flow<List<OfficialClassEntity>>

    @Query("SELECT * FROM official_classes WHERE isHidden = 1")
    fun getHiddenOfficialClasses(): Flow<List<OfficialClassEntity>>

    @Query("UPDATE official_classes SET isHidden = :hidden WHERE slotId = :slotId")
    suspend fun setOfficialClassHidden(slotId: String, hidden: Boolean)

    @Query("DELETE FROM official_classes WHERE slotId = :slotId")
    suspend fun deleteOfficialClass(slotId: String)

    @Query("DELETE FROM official_classes WHERE courseId = :courseId")
    suspend fun deleteOfficialClassesByCourseId(courseId: String)

    @Query("DELETE FROM official_classes")
    suspend fun wipeOfficialClasses()

    // ==========================================
    // Official Attendance
    // ==========================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfficialAttendance(records: List<OfficialAttendanceEntity>)

    @Query("SELECT * FROM official_attendance")
    fun getAllOfficialAttendance(): Flow<List<OfficialAttendanceEntity>>

    @Query("DELETE FROM official_attendance WHERE date = :date AND slotId = :slotId")
    suspend fun deleteOfficialAttendanceForSession(date: String, slotId: String)

    @Query("DELETE FROM official_attendance")
    suspend fun wipeOfficialAttendance()

    // ==========================================
    // Database Wipe Operations
    // ==========================================
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
        wipeOfficialClasses()
        wipeOfficialAttendance()
    }
}
