package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ImportTimetableData(
    val teacher: TeacherImport?,
    val batches: List<BatchImport>?
)

@JsonClass(generateAdapter = true)
data class TeacherImport(
    val name: String?,
    val id: String?
)

@JsonClass(generateAdapter = true)
data class BatchImport(
    val batchId: String?,
    val year: String?,
    val semester: String?,
    val course: CourseImport?,
    val section: String?,
    val location: String?,
    val weeklySchedule: List<ScheduleImport>?,
    val students: List<StudentImport>?
)

@JsonClass(generateAdapter = true)
data class CourseImport(
    val code: String?,
    val name: String?
)

@JsonClass(generateAdapter = true)
data class ScheduleImport(
    val day: String?,
    val time: String?,
    val location: String?
)

@JsonClass(generateAdapter = true)
data class StudentImport(
    val id: String?,
    val name: String?,
    val rollNumber: String?
)
