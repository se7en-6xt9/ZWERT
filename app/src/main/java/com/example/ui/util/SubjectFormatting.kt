package com.example.ui.util

import com.example.data.CourseEntity
import java.util.Locale

object SubjectFormatting {

    private val commonStopWords = setOf(
        "and", "or", "to", "for", "of", "in", "the", "a", "an", "with", "&", "on", "at", "by"
    )

    private val knownAcronymMap = mapOf(
        "database management systems" to "DBMS",
        "database management system" to "DBMS",
        "data structures & algorithms" to "DSA",
        "data structures and algorithms" to "DSA",
        "data structures" to "DSA",
        "operating systems" to "OS",
        "operating system" to "OS",
        "computer networks" to "CN",
        "computer network" to "CN",
        "software engineering" to "SE",
        "artificial intelligence" to "AI",
        "machine learning" to "ML",
        "deep learning" to "DL",
        "computer architecture" to "COA",
        "computer organization and architecture" to "COA",
        "object oriented programming" to "OOP",
        "object oriented programming using java" to "OOPJ",
        "object oriented programming with c++" to "OOPC",
        "discrete mathematics" to "DM",
        "digital electronics" to "DE",
        "digital logic design" to "DLD",
        "design and analysis of algorithms" to "DAA",
        "theory of computation" to "TOC",
        "compiler design" to "CD",
        "information security" to "IS",
        "cyber security" to "CS",
        "cloud computing" to "CC",
        "web technologies" to "WT",
        "internet of things" to "IOT",
        "introduction to biology for engineers" to "IBE",
        "biology for engineers" to "BFE",
        "engineering mathematics" to "M-I",
        "engineering physics" to "EP",
        "engineering chemistry" to "EC",
        "environmental science" to "EVS"
    )

    /**
     * Generates a clean, short 2-5 letter abbreviation/code from a subject name or code.
     * Used on compact dashboard cards to avoid multi-line text wrapping.
     */
    fun getShortLabel(course: CourseEntity?, fallbackCodeOrId: String = ""): String {
        val courseCode = course?.code?.trim() ?: ""
        val courseName = course?.name?.trim() ?: ""

        // 1. If explicit code exists and is reasonably short (<= 8 chars, e.g. "CS301", "DBMS", "EC-201"), use it
        if (courseCode.isNotBlank() && courseCode.length <= 8 && !courseCode.equals(courseName, ignoreCase = true)) {
            return courseCode.uppercase(Locale.ENGLISH)
        }

        // 2. Check name
        val nameToUse = courseName.ifBlank { courseCode.ifBlank { fallbackCodeOrId } }
        return generateAbbreviation(nameToUse)
    }

    /**
     * Generate abbreviation from a raw string name.
     */
    fun generateAbbreviation(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return "LEC"

        val lower = trimmed.lowercase(Locale.ENGLISH)
        // Check known map
        knownAcronymMap[lower]?.let { return it }

        // If it's already an uppercase code / short acronym like "CS301", "MATH101", "DBMS", "OS"
        if (trimmed.length <= 6 && !trimmed.contains(" ")) {
            return trimmed.uppercase(Locale.ENGLISH)
        }

        // Split by words
        val words = trimmed.split(Regex("[\\s-_/]+")).filter { it.isNotBlank() }

        if (words.size == 1) {
            val single = words[0]
            return if (single.length <= 6) {
                single.uppercase(Locale.ENGLISH)
            } else {
                single.take(4).uppercase(Locale.ENGLISH)
            }
        }

        // Multi-word: check significant words first
        val significantWords = words.filter { it.lowercase(Locale.ENGLISH) !in commonStopWords }
        val targetWords = if (significantWords.isNotEmpty()) significantWords else words

        val acronym = targetWords.mapNotNull { word ->
            word.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        }.joinToString("")

        if (acronym.length in 2..5) {
            return acronym
        }

        if (acronym.length > 5) {
            return acronym.take(4)
        }

        // Fallback: take first 4 chars of the trimmed name
        return trimmed.take(4).uppercase(Locale.ENGLISH)
    }

    /**
     * Gets the full subject name to show on detail views or expanded states.
     */
    fun getFullName(course: CourseEntity?, fallbackCodeOrId: String = ""): String {
        val name = course?.name?.trim() ?: ""
        if (name.isNotBlank()) return name

        val code = course?.code?.trim() ?: ""
        if (code.isNotBlank()) return code

        return fallbackCodeOrId.ifBlank { "Class Lecture" }
    }
}
