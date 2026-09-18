package com.example.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

data class CsvParseResult(
    val students: List<StudentImport>,
    val totalRows: Int,
    val validCount: Int,
    val duplicateCount: Int,
    val errorRows: List<Pair<Int, String>>, // 1-based row number -> reason
    val headers: List<String>,
    val nameColumnIndex: Int,
    val rollColumnIndex: Int,
    val emailColumnIndex: Int = -1,
    val delimiter: Char,
    val rawPreviewRows: List<List<String>>
)

object CsvStudentParser {

    private val NAME_KEYWORDS = setOf(
        "name", "studentname", "fullname", "student_name", "full_name",
        "student", "candidatename", "candidate", "firstname", "first_name"
    )

    private val ROLL_KEYWORDS = setOf(
        "roll", "rollno", "rollnumber", "roll_no", "roll_number",
        "reg", "regno", "reg_no", "registration", "registrationno", "registration_no",
        "enrollment", "enrollmentno", "enrollment_number", "enrollment_no",
        "id", "studentid", "student_id", "admno", "adm_no", "admissionno", "urn"
    )

    /**
     * Parses raw CSV content into a list of StudentImport objects with intelligent column matching
     * and detailed validation feedback.
     */
    fun parseCsv(
        csvContent: String,
        overrideNameIndex: Int? = null,
        overrideRollIndex: Int? = null,
        overrideEmailIndex: Int? = null,
        overrideHasHeader: Boolean? = null
    ): CsvParseResult {
        if (csvContent.isBlank()) {
            return CsvParseResult(
                students = emptyList(),
                totalRows = 0,
                validCount = 0,
                duplicateCount = 0,
                errorRows = emptyList(),
                headers = emptyList(),
                nameColumnIndex = -1,
                rollColumnIndex = -1,
                emailColumnIndex = -1,
                delimiter = ',',
                rawPreviewRows = emptyList()
            )
        }

        val delimiter = detectDelimiter(csvContent)
        val allRows = tokenizeCsv(csvContent, delimiter)
            .filter { row -> row.any { it.isNotBlank() } }

        if (allRows.isEmpty()) {
            return CsvParseResult(
                students = emptyList(),
                totalRows = 0,
                validCount = 0,
                duplicateCount = 0,
                errorRows = emptyList(),
                headers = emptyList(),
                nameColumnIndex = -1,
                rollColumnIndex = -1,
                emailColumnIndex = -1,
                delimiter = delimiter,
                rawPreviewRows = emptyList()
            )
        }

        // Detect if first row is header
        val firstRow = allRows[0]
        val detectedHeaderIndices = detectColumnIndicesFromHeader(firstRow)
        val isFirstRowHeader = overrideHasHeader ?: (detectedHeaderIndices != null)

        var nameCol = overrideNameIndex ?: detectedHeaderIndices?.first ?: -1
        var rollCol = overrideRollIndex ?: detectedHeaderIndices?.second ?: -1
        var emailCol = overrideEmailIndex ?: if (isFirstRowHeader) {
            firstRow.indexOfFirst { col ->
                val c = col.trim().lowercase()
                c.contains("email") || c.contains("mail")
            }
        } else -1

        val dataRows = if (isFirstRowHeader) allRows.drop(1) else allRows
        val headers = if (isFirstRowHeader) firstRow else List(firstRow.size) { "Column ${it + 1}" }

        // If columns still not identified, infer from data rows
        if (nameCol == -1 || rollCol == -1 || nameCol == rollCol) {
            val inferred = inferColumnIndices(dataRows)
            if (nameCol == -1) nameCol = inferred.first
            if (rollCol == -1) rollCol = inferred.second
        }

        // Final fallback: column 0 = Name, column 1 = Roll, or vice versa
        if (nameCol == -1 && rollCol == -1) {
            if (firstRow.size >= 2) {
                // Heuristic check: does column 0 look like roll number?
                val col0RollScore = dataRows.take(10).count { row ->
                    val v = row.getOrNull(0) ?: ""
                    isLikelyRollNumber(v)
                }
                val col1RollScore = dataRows.take(10).count { row ->
                    val v = row.getOrNull(1) ?: ""
                    isLikelyRollNumber(v)
                }
                if (col0RollScore > col1RollScore) {
                    rollCol = 0
                    nameCol = 1
                } else {
                    nameCol = 0
                    rollCol = 1
                }
            } else {
                nameCol = 0
                rollCol = 0
            }
        } else if (nameCol == -1) {
            nameCol = if (rollCol == 0) 1 else 0
        } else if (rollCol == -1) {
            rollCol = if (nameCol == 0) 1 else 0
        }

        // If emailCol still not determined, auto-detect from data rows
        if (emailCol == -1) {
            val maxCols = dataRows.maxOfOrNull { it.size } ?: 0
            var bestEmailCol = -1
            var bestCount = 0
            for (c in 0 until maxCols) {
                if (c == nameCol || c == rollCol) continue
                val count = dataRows.take(15).count { row ->
                    val v = row.getOrNull(c)?.trim() ?: ""
                    v.contains("@") && v.contains(".")
                }
                if (count > bestCount) {
                    bestCount = count
                    bestEmailCol = c
                }
            }
            if (bestCount > 0) {
                emailCol = bestEmailCol
            }
        }

        val parsedStudents = mutableListOf<StudentImport>()
        val errorRows = mutableListOf<Pair<Int, String>>()
        val seenRollNumbers = mutableSetOf<String>()
        var duplicateCount = 0

        dataRows.forEachIndexed { index, row ->
            val rowNum = if (isFirstRowHeader) index + 2 else index + 1
            val rawName = row.getOrNull(nameCol)?.trim()?.removeSurrounding("\"") ?: ""
            val rawRoll = row.getOrNull(rollCol)?.trim()?.removeSurrounding("\"") ?: ""

            if (rawName.isBlank() && rawRoll.isBlank()) {
                // Empty row, ignore
                return@forEachIndexed
            }

            if (rawName.isBlank()) {
                errorRows.add(Pair(rowNum, "Missing student name"))
                return@forEachIndexed
            }

            val finalRoll = if (rawRoll.isBlank()) {
                // Auto-generate a sequence roll if missing
                String.format("GEN%03d", index + 1)
            } else {
                rawRoll
            }

            val normalizedRoll = finalRoll.lowercase().replace(" ", "")
            if (seenRollNumbers.contains(normalizedRoll)) {
                duplicateCount++
            } else {
                seenRollNumbers.add(normalizedRoll)
            }

            val studentId = "student_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val rawEmail = if (emailCol != -1) row.getOrNull(emailCol)?.trim()?.removeSurrounding("\"")?.takeIf { it.contains("@") } else null
            parsedStudents.add(
                StudentImport(
                    id = studentId,
                    name = cleanStudentName(rawName),
                    rollNumber = finalRoll,
                    email = rawEmail?.lowercase()
                )
            )
        }

        return CsvParseResult(
            students = parsedStudents,
            totalRows = dataRows.size,
            validCount = parsedStudents.size,
            duplicateCount = duplicateCount,
            errorRows = errorRows,
            headers = headers,
            nameColumnIndex = nameCol,
            rollColumnIndex = rollCol,
            emailColumnIndex = emailCol,
            delimiter = delimiter,
            rawPreviewRows = allRows.take(6)
        )
    }

    /**
     * Reads and parses an InputStream from ContentResolver.
     */
    fun parseStream(
        inputStream: InputStream,
        overrideNameIndex: Int? = null,
        overrideRollIndex: Int? = null,
        overrideEmailIndex: Int? = null,
        overrideHasHeader: Boolean? = null
    ): CsvParseResult {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val text = reader.readText()
        return parseCsv(text, overrideNameIndex, overrideRollIndex, overrideEmailIndex, overrideHasHeader)
    }

    private fun detectDelimiter(content: String): Char {
        val sampleLines = content.lines().take(10).filter { it.isNotBlank() }
        val delimiters = listOf(',', ';', '\t', '|')
        var bestDelimiter = ','
        var maxCount = -1

        for (delim in delimiters) {
            val count = sampleLines.sumOf { line ->
                countDelimiterOccurrences(line, delim)
            }
            if (count > maxCount) {
                maxCount = count
                bestDelimiter = delim
            }
        }
        return bestDelimiter
    }

    private fun countDelimiterOccurrences(line: String, delim: Char): Int {
        var count = 0
        var insideQuotes = false
        for (ch in line) {
            if (ch == '"') {
                insideQuotes = !insideQuotes
            } else if (ch == delim && !insideQuotes) {
                count++
            }
        }
        return count
    }

    private fun tokenizeCsv(content: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var insideQuotes = false
        var i = 0
        val length = content.length

        while (i < length) {
            val ch = content[i]

            if (ch == '"') {
                if (insideQuotes && i + 1 < length && content[i + 1] == '"') {
                    // Escaped quote
                    currentField.append('"')
                    i++
                } else {
                    insideQuotes = !insideQuotes
                }
            } else if (ch == delimiter && !insideQuotes) {
                currentRow.add(currentField.toString().trim())
                currentField.clear()
            } else if ((ch == '\r' || ch == '\n') && !insideQuotes) {
                if (ch == '\r' && i + 1 < length && content[i + 1] == '\n') {
                    i++ // Skip \r in \r\n
                }
                currentRow.add(currentField.toString().trim())
                currentField.clear()
                if (currentRow.any { it.isNotBlank() }) {
                    rows.add(currentRow.toList())
                }
                currentRow.clear()
            } else {
                currentField.append(ch)
            }
            i++
        }

        // Add last field and row if remaining
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString().trim())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow.toList())
            }
        }

        return rows
    }

    private fun detectColumnIndicesFromHeader(row: List<String>): Pair<Int, Int>? {
        var nameIndex = -1
        var rollIndex = -1

        row.forEachIndexed { index, rawHeader ->
            val normalized = rawHeader.lowercase().replace(" ", "").replace("_", "").replace(".", "")
            if (nameIndex == -1 && NAME_KEYWORDS.any { normalized.contains(it) }) {
                nameIndex = index
            }
            if (rollIndex == -1 && ROLL_KEYWORDS.any { normalized.contains(it) }) {
                rollIndex = index
            }
        }

        return if (nameIndex != -1 && rollIndex != -1 && nameIndex != rollIndex) {
            Pair(nameIndex, rollIndex)
        } else if (nameIndex != -1 || rollIndex != -1) {
            // One matched
            val finalName = if (nameIndex != -1) nameIndex else if (rollIndex == 0) 1 else 0
            val finalRoll = if (rollIndex != -1) rollIndex else if (finalName == 0) 1 else 0
            Pair(finalName, finalRoll)
        } else {
            null
        }
    }

    private fun inferColumnIndices(rows: List<List<String>>): Pair<Int, Int> {
        val sample = rows.take(15)
        if (sample.isEmpty()) return Pair(0, 1)

        val columnCount = sample.maxOfOrNull { it.size } ?: 2
        var bestRollCol = -1
        var highestRollScore = -1

        for (col in 0 until columnCount) {
            val rollScore = sample.count { row ->
                val text = row.getOrNull(col) ?: ""
                isLikelyRollNumber(text)
            }
            if (rollScore > highestRollScore) {
                highestRollScore = rollScore
                bestRollCol = col
            }
        }

        var bestNameCol = -1
        var highestNameScore = -1

        for (col in 0 until columnCount) {
            if (col == bestRollCol) continue
            val nameScore = sample.count { row ->
                val text = row.getOrNull(col) ?: ""
                isLikelyName(text)
            }
            if (nameScore > highestNameScore) {
                highestNameScore = nameScore
                bestNameCol = col
            }
        }

        if (bestRollCol == -1) bestRollCol = 1
        if (bestNameCol == -1) bestNameCol = if (bestRollCol == 0) 1 else 0

        return Pair(bestNameCol, bestRollCol)
    }

    private fun isLikelyRollNumber(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false
        // Roll numbers typically: contain digits, no spaces, length 2..20 (e.g., 24BCS001, 101, CS21, 2024-CSE-012)
        val hasDigits = clean.any { it.isDigit() }
        val hasNoSpaces = !clean.contains(" ")
        val lengthOk = clean.length in 2..22
        return (hasDigits && hasNoSpaces && lengthOk) || clean.all { it.isDigit() }
    }

    private fun isLikelyName(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false
        // Names usually: mostly letters, may contain spaces or dots (e.g., "Aarav Sharma", "P. K. Verma")
        val lettersCount = clean.count { it.isLetter() || it == ' ' || it == '.' || it == '\'' || it == '-' }
        return lettersCount.toDouble() / clean.length >= 0.8 && clean.length in 3..60
    }

    private fun cleanStudentName(name: String): String {
        return name
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                // Capitalize each word nicely
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }

    /**
     * Realistic sample CSV data for faculty to test or load immediately with one tap.
     */
    fun getSampleCsvString(): String = """Roll Number, Student Name, Student Email
24BCS001, Aman Kumar, student.demo@campus.edu
24BCS002, Aarav Sharma, aarav.sharma@campus.edu
24BCS003, Ananya Patel, ananya.patel@campus.edu
24BCS004, Rohan Mehta, rohan.mehta@campus.edu
24BCS005, Ishaan Verma, ishaan.verma@campus.edu
24BCS006, Diya Iyer, diya.iyer@campus.edu
24BCS007, Sneha Kulkarni, sneha.k@campus.edu
24BCS008, Aditya Rao, aditya.rao@campus.edu
24BCS009, Kavya Deshmukh, kavya.d@campus.edu
24BCS010, Kabir Sen, kabir.sen@campus.edu
24BCS011, Riya Mukherjee, riya.m@campus.edu
24BCS012, Tanmay Joshi, tanmay.j@campus.edu
24BCS013, Meera Nambiar, meera.n@campus.edu
24BCS014, Arjun Kapoor, arjun.k@campus.edu
24BCS015, Pooja Hegde, pooja.h@campus.edu
24BCS016, Nikhil Reddy, nikhil.r@campus.edu
24BCS017, Shruti Bhat, shruti.b@campus.edu
24BCS018, Varun Chauhan, varun.c@campus.edu
24BCS019, Aniket Gupta, aniket.g@campus.edu
24BCS020, Tara Pillai, tara.p@campus.edu"""
}
