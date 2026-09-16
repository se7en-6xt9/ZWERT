package com.example.ui.util

import com.example.data.CourseEntity
import java.util.Locale

object SubjectFormatting {

    private val commonStopWords = setOf(
        "and", "or", "to", "for", "of", "in", "the", "a", "an", "with", "&", "on", "at", "by", "using", "through"
    )

    private val knownAcronymMap = mapOf(
        "database management systems" to "DBMS",
        "database management system" to "DBMS",
        "database management" to "DBMS",
        "dbms" to "DBMS",
        "data structures & algorithms" to "DSA",
        "data structures and algorithms" to "DSA",
        "data structures" to "DSA",
        "data structure" to "DSA",
        "dsa" to "DSA",
        "operating systems" to "OS",
        "operating system" to "OS",
        "os" to "OS",
        "computer networks" to "CN",
        "computer network" to "CN",
        "cn" to "CN",
        "software engineering" to "SE",
        "software engineering & testing" to "SE",
        "artificial intelligence" to "AI",
        "machine learning" to "ML",
        "deep learning" to "DL",
        "artificial intelligence and machine learning" to "AIML",
        "artificial intelligence & machine learning" to "AIML",
        "computer architecture" to "COA",
        "computer organization and architecture" to "COA",
        "computer organization & architecture" to "COA",
        "computer organization" to "CO",
        "coa" to "COA",
        "object oriented programming" to "OOP",
        "object oriented programming using java" to "OOPJ",
        "object oriented programming with java" to "OOPJ",
        "object oriented programming with c++" to "OOPC",
        "object oriented programming using c++" to "OOPC",
        "oop" to "OOP",
        "discrete mathematics" to "DM",
        "discrete structures" to "DS",
        "digital electronics" to "DE",
        "digital logic design" to "DLD",
        "digital circuits" to "DC",
        "design and analysis of algorithms" to "DAA",
        "design & analysis of algorithms" to "DAA",
        "theory of computation" to "TOC",
        "automata theory" to "AT",
        "compiler design" to "CD",
        "information security" to "IS",
        "cyber security" to "CS",
        "network security" to "NS",
        "cloud computing" to "CC",
        "web technologies" to "WT",
        "web development" to "WD",
        "internet of things" to "IOT",
        "introduction to biology for engineers" to "IBE",
        "biology for engineers" to "IBE",
        "introduction to bio engineering" to "IBE",
        "bio engineering" to "BE",
        "bio-engineering" to "BE",
        "biology" to "BIO",
        "engineering mathematics" to "MATH",
        "engineering mathematics i" to "M-I",
        "engineering mathematics ii" to "M-II",
        "engineering mathematics iii" to "M-III",
        "engineering physics" to "EP",
        "physics" to "PHY",
        "engineering chemistry" to "EC",
        "chemistry" to "CHEM",
        "environmental science" to "EVS",
        "environmental studies" to "EVS",
        "environmental engineering" to "EE",
        "evs" to "EVS",
        "microprocessors and microcontrollers" to "MPMC",
        "microprocessor and microcontroller" to "MPMC",
        "microprocessors" to "MP",
        "microcontrollers" to "MC",
        "signals and systems" to "SS",
        "signals & systems" to "SS",
        "digital signal processing" to "DSP",
        "control systems" to "CS",
        "power systems" to "PS",
        "electrical machines" to "EM",
        "basic electrical engineering" to "BEE",
        "basic electronics engineering" to "BXE",
        "engineering graphics" to "EG",
        "engineering mechanics" to "EM",
        "professional ethics" to "PE",
        "human values and professional ethics" to "HVPE",
        "technical communication" to "TC",
        "soft skills" to "SS"
    )

    /**
     * Generates a clean short 2-5 letter abbreviation/acronym directly from the subject name.
     * Prioritizes the course name acronym (e.g. "Introduction to Bio Engineering" -> "IBE", "Operating Systems" -> "OS").
     */
    fun getShortLabel(course: CourseEntity?, fallbackCodeOrId: String = ""): String {
        val courseName = course?.name?.trim() ?: ""
        val courseCode = course?.code?.trim() ?: ""

        // 1. Always prioritize generating short acronym from the full course name if available
        if (courseName.isNotBlank() && !courseName.equals("Unknown Course", ignoreCase = true)) {
            return generateAbbreviation(courseName)
        }

        // 2. If course name is missing but code exists, generate abbreviation from code
        if (courseCode.isNotBlank() && !courseCode.equals("Unknown Code", ignoreCase = true)) {
            return generateAbbreviation(courseCode)
        }

        // 3. Fallback to extracting meaningful subject part from fallbackCodeOrId
        val cleanFallback = extractSubjectFromId(fallbackCodeOrId)
        return generateAbbreviation(cleanFallback)
    }

    /**
     * Extracts subject name or code from composite course IDs like "CSE-4SEM-DBMS" or "2024-CSE-4SEM-Operating Systems".
     */
    fun extractSubjectFromId(id: String): String {
        val trimmed = id.trim()
        if (trimmed.isBlank()) return "LEC"

        val parts = trimmed.split("-")
        if (parts.size >= 4 && parts[1].contains("SEM", ignoreCase = true)) {
            return parts.drop(3).joinToString("-")
        }
        if (parts.size == 3 && parts[1].contains("SEM", ignoreCase = true)) {
            return parts[2]
        }
        return trimmed
    }

    /**
     * Generate abbreviation from a raw string name.
     * E.g. "Introduction to Bio Engineering" -> "IBE", "Operating Systems" -> "OS", "Database Management Systems" -> "DBMS".
     */
    fun generateAbbreviation(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return "LEC"

        val lower = trimmed.lowercase(Locale.ENGLISH)
        // 1. Check known acronym dictionary
        knownAcronymMap[lower]?.let { return it }

        // 2. If it's already an uppercase acronym or short word (2-5 letters without digits/spaces)
        if (trimmed.length in 2..5 && !trimmed.contains(" ") && trimmed.all { it.isLetter() }) {
            return trimmed.uppercase(Locale.ENGLISH)
        }

        // 3. Split by whitespace and separators
        val words = trimmed.split(Regex("[\\s-_/]+")).filter { it.isNotBlank() }

        if (words.size == 1) {
            val single = words[0]
            val singleLower = single.lowercase(Locale.ENGLISH)
            knownAcronymMap[singleLower]?.let { return it }

            return if (single.length <= 5) {
                single.uppercase(Locale.ENGLISH)
            } else {
                single.take(3).uppercase(Locale.ENGLISH)
            }
        }

        // 4. Multi-word: filter out common stop words ("to", "for", "and", "of", "in", "the", "a", "an", "with", etc.)
        val significantWords = words.filter { it.lowercase(Locale.ENGLISH) !in commonStopWords }
        val targetWords = if (significantWords.isNotEmpty()) significantWords else words

        // Build acronym from first letter of each significant word
        val acronym = targetWords.mapNotNull { word ->
            word.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        }.joinToString("")

        if (acronym.length in 2..5) {
            return acronym
        }

        if (acronym.length > 5) {
            return acronym.take(4)
        }

        // 5. Fallback: take first 3 chars
        return trimmed.take(3).uppercase(Locale.ENGLISH)
    }

    /**
     * Gets the full subject name to show on detail views or subtitle states.
     */
    fun getFullName(course: CourseEntity?, fallbackCodeOrId: String = ""): String {
        val name = course?.name?.trim() ?: ""
        if (name.isNotBlank() && !name.equals("Unknown Course", ignoreCase = true)) {
            return name
        }

        val code = course?.code?.trim() ?: ""
        if (code.isNotBlank() && !code.equals("Unknown Code", ignoreCase = true)) {
            return code
        }

        val fromId = extractSubjectFromId(fallbackCodeOrId)
        if (fromId.isNotBlank() && fromId != "LEC") {
            return fromId
        }

        return "Class Lecture"
    }
}
