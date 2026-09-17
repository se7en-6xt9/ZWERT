package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline-first synchronization status tracking for multi-device sync engine.
 */
enum class SyncStatus {
    SYNCED,
    PENDING_INSERT,
    PENDING_UPDATE,
    PENDING_DELETE
}

/**
 * Multi-tenant Course entity linked to a specific authenticated teacher or tenant.
 */
@Entity(
    tableName = "courses",
    indices = [
        Index(value = ["teacherId"]),
        Index(value = ["code"]),
        Index(value = ["syncStatus"])
    ]
)
data class CourseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val credits: Int,
    val teacherId: String = "",
    val semester: String = "",
    val section: String = "",
    val userId: String = "",
    val syncStatus: String = SyncStatus.SYNCED.name,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String = ""
)

/**
 * Student enrolled in a batch or course.
 */
@Entity(
    tableName = "students",
    indices = [
        Index(value = ["courseId"]),
        Index(value = ["rollNumber"]),
        Index(value = ["name"]),
        Index(value = ["syncStatus"])
    ]
)
data class StudentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rollNumber: String,
    val courseId: String,
    val email: String = "",
    val userId: String = "",
    val syncStatus: String = SyncStatus.SYNCED.name,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String = ""
)

/**
 * Weekly lecture or practical schedule slot.
 */
@Entity(
    tableName = "schedule_slots",
    indices = [
        Index(value = ["courseId"]),
        Index(value = ["dayOfWeek"]),
        Index(value = ["syncStatus"])
    ]
)
data class ScheduleSlotEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val room: String,
    val section: String,
    val userId: String = "",
    val syncStatus: String = SyncStatus.SYNCED.name,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String = ""
)

/**
 * Individual attendance event with multi-device sync audit and status.
 */
@Entity(
    tableName = "attendance",
    indices = [
        Index(value = ["courseId"]),
        Index(value = ["scheduleSlotId"]),
        Index(value = ["studentId"]),
        Index(value = ["date"]),
        Index(value = ["date", "scheduleSlotId"]),
        Index(value = ["studentId", "courseId"]),
        Index(value = ["date", "scheduleSlotId", "studentId"]),
        Index(value = ["syncStatus"])
    ]
)
data class AttendanceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String = "", // e.g., "YYYY-MM-DD"
    val scheduleSlotId: String = "",
    val studentId: String = "",
    val status: String = "", // "P", "A", "L"
    val courseId: String = "",
    val sessionId: String = "",
    val userId: String = "",
    val syncStatus: String = SyncStatus.SYNCED.name,
    val markedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncVersion: Long = 1L,
    val deviceId: String = ""
)

/**
 * Academic profile for student or faculty with role-specific attributes.
 */
@Entity(tableName = "users_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val authId: String = "",
    val role: String = "student", // "student" or "teacher"
    val name: String = "",
    val email: String = "",
    val department: String = "",
    val rollOrEmpId: String = "",
    val designation: String = "",
    val cabinNo: String = "",
    val semester: String = "",
    val section: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String = "",
    val syncStatus: String = SyncStatus.SYNCED.name,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String = ""
)

/**
 * Attendance session entity corresponding to a lecture meeting.
 */
@Entity(tableName = "attendance_sessions")
data class AttendanceSessionEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val teacherId: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val isActive: Boolean = true,
    val userId: String = "",
    val syncStatus: String = SyncStatus.SYNCED.name,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String = ""
)

/**
 * Student-course enrollment mapping.
 */
@Entity(tableName = "enrollments")
data class EnrollmentEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val studentId: String,
    val enrolledAt: Long = System.currentTimeMillis(),
    val userId: String = "",
    val syncStatus: String = SyncStatus.SYNCED.name,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String = ""
)

/**
 * Official timetable lecture pushed by faculty members matching student's email.
 */
@Entity(
    tableName = "official_classes",
    indices = [
        Index(value = ["dayOfWeek"]),
        Index(value = ["courseId"]),
        Index(value = ["isHidden"])
    ]
)
data class OfficialClassEntity(
    @PrimaryKey val slotId: String,
    val courseId: String,
    val courseName: String,
    val courseCode: String,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val room: String,
    val section: String,
    val facultyName: String,
    val facultyEmail: String = "",
    val isHidden: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Official attendance record marked by faculty for a student session.
 */
@Entity(
    tableName = "official_attendance",
    indices = [
        Index(value = ["date", "slotId"])
    ]
)
data class OfficialAttendanceEntity(
    @PrimaryKey val id: String, // "${date}_${slotId}"
    val date: String,
    val slotId: String,
    val courseId: String,
    val courseName: String,
    val status: String, // "P", "A", "C", "CANCELLED"
    val markedAt: Long = System.currentTimeMillis()
)


