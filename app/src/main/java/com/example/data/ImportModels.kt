package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ImportTimetableData(
    val teacher: TeacherImport? = null,
    val batches: List<BatchImport>? = null,
    val missingFields: List<String>? = null,
    val summary: String? = null
)

@JsonClass(generateAdapter = true)
data class TeacherImport(
    val name: String? = null,
    val id: String? = null
)

@JsonClass(generateAdapter = true)
data class BatchImport(
    val batchId: String? = null,
    val year: String? = null,
    val semester: String? = null,
    val course: CourseImport? = null,
    val section: String? = null,
    val location: String? = null,
    val weeklySchedule: List<ScheduleImport>? = null,
    val students: List<StudentImport>? = null
)

@JsonClass(generateAdapter = true)
data class CourseImport(
    val code: String? = null,
    val name: String? = null,
    val shortName: String? = null
)

@JsonClass(generateAdapter = true)
data class ScheduleImport(
    val day: String? = null,
    val time: String? = null,
    val location: String? = null
)

@JsonClass(generateAdapter = true)
data class StudentImport(
    val id: String? = null,
    val name: String? = null,
    val rollNumber: String? = null,
    val email: String? = null
)
