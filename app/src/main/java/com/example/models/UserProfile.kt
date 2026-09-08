package com.example.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserProfile(
    val firstName: String = "",
    val lastName: String = "",
    val college: String = "",
    val department: String = ""
)
