package com.example.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CourseUpload(
    val id: String,
    val name: String,
    val code: String,
    val credits: Int,
    val students: List<StudentUpload>
)

@JsonClass(generateAdapter = true)
data class StudentUpload(
    val id: String,
    val name: String,
    val rollNumber: String
)

@JsonClass(generateAdapter = true)
data class ScheduleSlotUpload(
    val id: String,
    val courseId: String,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val room: String,
    val section: String? = null
)

@JsonClass(generateAdapter = true)
data class UploadData(
    val courses: List<CourseUpload>,
    val weeklySchedule: List<ScheduleSlotUpload>
)
