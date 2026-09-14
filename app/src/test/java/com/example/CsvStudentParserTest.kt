package com.example

import com.example.data.CsvStudentParser
import org.junit.Assert.*
import org.junit.Test

class CsvStudentParserTest {

    @Test
    fun testStandardCsvWithHeaders() {
        val csv = """Roll Number, Student Name, Department
24BCS001, Aarav Sharma, Computer Science
24BCS002, Ananya Patel, Computer Science
24BCS003, Rohan Mehta, Computer Science"""

        val result = CsvStudentParser.parseCsv(csv)
        assertEquals(3, result.validCount)
        assertEquals("24BCS001", result.students[0].rollNumber)
        assertEquals("Aarav Sharma", result.students[0].name)
        assertEquals("24BCS002", result.students[1].rollNumber)
        assertEquals("Ananya Patel", result.students[1].name)
        assertEquals(0, result.duplicateCount)
    }

    @Test
    fun testInvertedColumns() {
        val csv = """Full Name, Reg No
Ishaan Verma, CS-101
Diya Iyer, CS-102"""

        val result = CsvStudentParser.parseCsv(csv)
        assertEquals(2, result.validCount)
        assertEquals("Ishaan Verma", result.students[0].name)
        assertEquals("CS-101", result.students[0].rollNumber)
        assertEquals("Diya Iyer", result.students[1].name)
        assertEquals("CS-102", result.students[1].rollNumber)
    }

    @Test
    fun testSemicolonDelimiterWithQuotes() {
        val csv = """"Sharma, Aarav";24BCS001
"Patel, Ananya";24BCS002"""

        val result = CsvStudentParser.parseCsv(csv)
        assertEquals(2, result.validCount)
        assertEquals(';', result.delimiter)
        assertEquals("24BCS001", result.students[0].rollNumber)
    }

    @Test
    fun testDuplicateDetection() {
        val csv = """Roll, Name
24BCS001, Student One
24BCS001, Student Duplicate
24BCS002, Student Two"""

        val result = CsvStudentParser.parseCsv(csv)
        assertEquals(3, result.validCount)
        assertEquals(1, result.duplicateCount)
    }

    @Test
    fun testSampleCsvString() {
        val sample = CsvStudentParser.getSampleCsvString()
        val result = CsvStudentParser.parseCsv(sample)
        assertEquals(25, result.validCount)
        assertEquals(0, result.duplicateCount)
        assertEquals(0, result.errorRows.size)
    }
}
