package com.example.data

import androidx.room.Entity
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
@Entity(tableName = "courses")
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
@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rollNumber: String,
    val courseId: String,
    val userId: String = "",
    val syncStatus: String = SyncStatus.SYNCED.name,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String = ""
)

/**
 * Weekly lecture or practical schedule slot.
 */
@Entity(tableName = "schedule_slots")
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
@Entity(tableName = "attendance")
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

