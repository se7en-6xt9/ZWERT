package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val credits: Int
)

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rollNumber: String,
    val courseId: String
)

@Entity(tableName = "schedule_slots")
data class ScheduleSlotEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val room: String,
    val section: String
)

@Entity(tableName = "attendance")
data class AttendanceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String = "", // e.g., "YYYY-MM-DD"
    val scheduleSlotId: String = "",
    val studentId: String = "",
    val status: String = "" // "P", "A", "L"
)
