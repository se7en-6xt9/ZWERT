package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.AttendanceRecordEntity
import com.example.data.CourseEntity
import com.example.data.StudentEntity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

enum class ExportFormat(val extension: String, val mimeType: String, val displayName: String) {
    EXCEL("csv", "text/csv", "Excel Spreadsheet (.csv)"),
    WORD("doc", "application/msword", "Word Document (.doc)"),
    PDF("pdf", "application/pdf", "PDF Document (.pdf)")
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.PDF,
    val startDate: LocalDate = LocalDate.now().minusDays(30),
    val endDate: LocalDate = LocalDate.now(),
    val institutionName: String = "Department of Computer Science & Engineering",
    val facultyName: String = "Faculty Member"
)

data class StudentExportRow(
    val student: StudentEntity,
    val present: Int,
    val absent: Int,
    val late: Int,
    val attended: Int,
    val percentage: Double,
    val status: String,
    val remarks: String
)

object AttendanceExportHelper {

    fun exportAttendance(
        context: Context,
        course: CourseEntity?,
        students: List<StudentEntity>,
        attendanceRecords: List<AttendanceRecordEntity>,
        options: ExportOptions
    ): File? {
        if (students.isEmpty()) {
            Toast.makeText(context, "No students to export", Toast.LENGTH_SHORT).show()
            return null
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val courseCodeClean = (course?.code ?: "COURSE").replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val timestamp = System.currentTimeMillis()
        val fileName = "Attendance_${courseCodeClean}_${options.startDate}_to_${options.endDate}_$timestamp.${options.format.extension}"
        val file = File(exportDir, fileName)

        // Find all distinct session dates in the range
        val dateSet = mutableSetOf<String>()
        val startStr = options.startDate.toString()
        val endStr = options.endDate.toString()

        attendanceRecords.forEach { record ->
            if (record.date in startStr..endStr && record.date.isNotBlank()) {
                dateSet.add(record.date)
            }
        }

        // If no records exist in the range, populate at least with valid dates in the range
        val activeDates = if (dateSet.isNotEmpty()) {
            dateSet.sorted()
        } else {
            // Generate sequence of dates
            val list = mutableListOf<String>()
            var curr = options.startDate
            while (!curr.isAfter(options.endDate) && list.size <= 31) {
                list.add(curr.toString())
                curr = curr.plusDays(1)
            }
            list
        }

        // Fast lookup map: date -> studentId -> status
        val lookup = mutableMapOf<String, MutableMap<String, String>>()
        attendanceRecords.forEach { record ->
            val dateMap = lookup.getOrPut(record.date) { mutableMapOf() }
            dateMap[record.studentId] = record.status
        }

        return try {
            when (options.format) {
                ExportFormat.EXCEL -> generateCsvExcel(file, course, students, activeDates, lookup, options)
                ExportFormat.WORD -> generateWordDoc(file, course, students, activeDates, lookup, options)
                ExportFormat.PDF -> generatePdf(file, course, students, activeDates, lookup, options)
            }
            file
        } catch (e: Exception) {
            android.util.Log.e("AttendanceExport", "Failed to export ${options.format}: ${e.message}", e)
            Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            null
        }
    }

    fun shareExportedFile(context: Context, file: File, format: ExportFormat) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
                putExtra(Intent.EXTRA_TEXT, "Attendance Register export: ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Attendance via"))
        } catch (e: Exception) {
            android.util.Log.e("AttendanceExport", "Share failed: ${e.message}", e)
            Toast.makeText(context, "Could not open share menu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openExportedFile(context: Context, file: File, format: ExportFormat) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, format.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(viewIntent, "Open with"))
        } catch (e: Exception) {
            // Fallback to share if no dedicated viewer installed
            shareExportedFile(context, file, format)
        }
    }

    // =========================================================================
    // 1. EXCEL / SPREADSHEET (CSV) GENERATOR
    // =========================================================================
    private fun generateCsvExcel(
        file: File,
        course: CourseEntity?,
        students: List<StudentEntity>,
        dates: List<String>,
        lookup: Map<String, Map<String, String>>,
        options: ExportOptions
    ) {
        val totalClasses = dates.size

        // Precompute statistics
        var grandTotalPresent = 0
        var grandTotalAbsent = 0
        var grandTotalLate = 0
        var eligibleCount = 0
        var shortageCount = 0

        val studentStats = students.map { student ->
            var p = 0
            var a = 0
            var l = 0
            dates.forEach { d ->
                val status = lookup[d]?.get(student.id)?.uppercase() ?: "-"
                when (status) {
                    "P", "PRESENT" -> p++
                    "L", "LATE" -> l++
                    "A", "ABSENT" -> a++
                }
            }
            val attended = p + l
            val pct = if (totalClasses > 0) (attended * 100.0 / totalClasses) else 0.0
            if (pct >= 75.0) eligibleCount++ else shortageCount++

            grandTotalPresent += p
            grandTotalAbsent += a
            grandTotalLate += l

            val statusStr = when {
                pct >= 75.0 -> "ELIGIBLE (>=75%)"
                pct >= 65.0 -> "CONDITIONAL (<75%)"
                else -> "CRITICAL SHORTAGE (<65%)"
            }

            val remarksStr = when {
                pct >= 85.0 -> "Good Standing"
                pct >= 75.0 -> "Regular"
                pct >= 65.0 -> "Warning Issued"
                else -> "Detention Alert"
            }

            StudentExportRow(student, p, a, l, attended, pct, statusStr, remarksStr)
        }

        val totalPossibleSlots = students.size * totalClasses
        val totalAttendedGrand = grandTotalPresent + grandTotalLate
        val classAvgPct = if (totalPossibleSlots > 0) (totalAttendedGrand * 100.0 / totalPossibleSlots) else 0.0

        FileOutputStream(file).use { fos ->
            // Write UTF-8 BOM so Excel immediately opens it with proper UTF-8 decoding
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                // Row 1: Document Title
                writer.append("\"ACADEMIC ATTENDANCE REGISTER - OFFICIAL RECORD\",,,,,,,,,,\n")
                
                // Rows 2-6: Structured Institutional Header Grid
                writer.append("\"Institution:\",\"${escapeCsv(options.institutionName)}\",,\"Academic Session:\",\"${LocalDate.now().year}-${LocalDate.now().year + 1}\",,,,\n")
                writer.append("\"Course Title:\",\"${escapeCsv(course?.name ?: "Course")}\",,\"Course Code:\",\"${escapeCsv(course?.code ?: "N/A")}\",,,,\n")
                writer.append("\"Faculty In-Charge:\",\"${escapeCsv(options.facultyName)}\",,\"Semester / Section:\",\"${escapeCsv(course?.semester ?: "Semester")} / ${escapeCsv(course?.section ?: "Batch")}\",,,,\n")
                writer.append("\"Duration Range:\",\"${options.startDate} to ${options.endDate}\",,\"Total Lectures Held:\",\"$totalClasses\",,,,\n")
                writer.append("\"Report Generated:\",\"${LocalDate.now()}\",,\"Enrolled Students:\",\"${students.size}\",,,,\n")
                writer.append(",,,,,,,,,,\n")

                // Rows 8-10: Executive Summary & Stat Highlights
                writer.append("\"EXECUTIVE ATTENDANCE SUMMARY\",,,,,,,,,,\n")
                writer.append("\"Statutory Benchmark:\",\"75.0% Minimum for Exam Eligibility\",,\"Class Average Attendance:\",\"${String.format(Locale.ENGLISH, "%.1f%%", classAvgPct)}\",,,,\n")
                writer.append("\"Eligible Students (>= 75%):\",\"$eligibleCount / ${students.size}\",,\"Attendance Defaulters (< 75%):\",\"$shortageCount / ${students.size}\",,,,\n")
                writer.append(",,,,,,,,,,\n")

                // Main Table Column Headers
                writer.append("\"S.No.\",\"Roll Number\",\"Student Name\"")
                dates.forEach { date ->
                    writer.append(",\"${formatShortDate(date)}\"")
                }
                writer.append(",\"Total Held\",\"Present (P)\",\"Absent (A)\",\"Leave/OD (L)\",\"Attendance %\",\"Eligibility Status\",\"Remarks\"\n")

                // Main Student Rows
                studentStats.forEachIndexed { index, row ->
                    writer.append("\"${index + 1}\",\"${escapeCsv(row.student.rollNumber)}\",\"${escapeCsv(row.student.name)}\"")
                    dates.forEach { date ->
                        val raw = lookup[date]?.get(row.student.id)?.uppercase() ?: "-"
                        val code = when (raw) {
                            "P", "PRESENT" -> "P"
                            "A", "ABSENT" -> "A"
                            "L", "LATE" -> "L"
                            else -> "-"
                        }
                        writer.append(",\"$code\"")
                    }
                    writer.append(",\"$totalClasses\",\"${row.present}\",\"${row.absent}\",\"${row.late}\",\"${String.format(Locale.ENGLISH, "%.1f%%", row.percentage)}\",\"${row.status}\",\"${row.remarks}\"\n")
                }

                // Table Bottom Aggregations: Day-wise totals
                writer.append("\"\",\"\",\"DAILY PRESENT (P)\"")
                dates.forEach { date ->
                    val pOnDate = students.count { s ->
                        val st = lookup[date]?.get(s.id)?.uppercase()
                        st == "P" || st == "PRESENT" || st == "L" || st == "LATE"
                    }
                    writer.append(",\"$pOnDate\"")
                }
                writer.append(",\"$totalPossibleSlots\",\"$grandTotalPresent\",\"$grandTotalAbsent\",\"$grandTotalLate\",\"${String.format(Locale.ENGLISH, "%.1f%%", classAvgPct)}\",\"-\",\"-\"\n")

                writer.append("\"\",\"\",\"DAILY ABSENT (A)\"")
                dates.forEach { date ->
                    val aOnDate = students.count { s ->
                        val st = lookup[date]?.get(s.id)?.uppercase()
                        st == "A" || st == "ABSENT"
                    }
                    writer.append(",\"$aOnDate\"")
                }
                writer.append(",\"-\",\"-\",\"-\",\"-\",\"-\",\"-\",\"-\"\n")

                writer.append("\"\",\"\",\"DAILY ATTENDANCE RATE (%)\"")
                dates.forEach { date ->
                    val pOnDate = students.count { s ->
                        val st = lookup[date]?.get(s.id)?.uppercase()
                        st == "P" || st == "PRESENT" || st == "L" || st == "LATE"
                    }
                    val dayRate = if (students.isNotEmpty()) (pOnDate * 100.0 / students.size) else 0.0
                    writer.append(",\"${String.format(Locale.ENGLISH, "%.1f%%", dayRate)}\"")
                }
                writer.append(",\"-\",\"-\",\"-\",\"-\",\"-\",\"-\",\"-\"\n")

                // Empty row before signatures
                writer.append(",,,,,,,,,,\n")
                writer.append(",,,,,,,,,,\n")

                // Official Endorsement Block
                writer.append("\"OFFICIAL VERIFICATION & ENDORSEMENT\",,,,,,,,,,\n")
                writer.append("\"Course Instructor / Faculty:\",\"${escapeCsv(options.facultyName)}\",,,,\"Head of Department (HOD):\",\"\"\n")
                writer.append("\"Faculty Signature:\",\"____________________________\",,,,\"HOD Signature:\",\"____________________________\"\n")
                writer.append("\"Date:\",\"${LocalDate.now()}\",,,,\"Official Seal / Stamp:\",\"[ SEAL ]\"\n")
                writer.flush()
            }
        }
    }

    // =========================================================================
    // 2. WORD DOCUMENT (.DOC / HTML DOCUMENT) GENERATOR
    // =========================================================================
    private fun generateWordDoc(
        file: File,
        course: CourseEntity?,
        students: List<StudentEntity>,
        dates: List<String>,
        lookup: Map<String, Map<String, String>>,
        options: ExportOptions
    ) {
        val totalClasses = dates.size
        var grandTotalPresent = 0
        var grandTotalAbsent = 0
        var grandTotalLate = 0
        var eligibleCount = 0
        var shortageCount = 0

        val studentStats = students.map { s ->
            var p = 0
            var a = 0
            var l = 0
            dates.forEach { d ->
                val status = lookup[d]?.get(s.id)?.uppercase() ?: "-"
                when (status) {
                    "P", "PRESENT" -> p++
                    "L", "LATE" -> l++
                    "A", "ABSENT" -> a++
                }
            }
            val attended = p + l
            val pct = if (totalClasses > 0) (attended * 100.0 / totalClasses) else 0.0
            if (pct >= 75.0) eligibleCount++ else shortageCount++

            grandTotalPresent += p
            grandTotalAbsent += a
            grandTotalLate += l

            StudentExportRow(s, p, a, l, attended, pct, if (pct >= 75.0) "ELIGIBLE" else "SHORTAGE", "")
        }

        val totalPossibleSlots = students.size * totalClasses
        val totalAttendedGrand = grandTotalPresent + grandTotalLate
        val classAvgPct = if (totalPossibleSlots > 0) (totalAttendedGrand * 100.0 / totalPossibleSlots) else 0.0

        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>
            <head>
            <meta charset='utf-8'>
            <title>Official Academic Attendance Register</title>
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size: 10pt; color: #0F172A; margin: 24px; background: #FFFFFF; }
                .header-banner { border-bottom: 3px solid #1E3A8A; padding-bottom: 12px; margin-bottom: 16px; }
                h1 { font-size: 17pt; color: #1E3A8A; margin: 0 0 4px 0; text-transform: uppercase; letter-spacing: 0.5px; }
                h2 { font-size: 12pt; color: #475569; margin: 0; font-weight: 500; }
                .kpi-grid { display: flex; width: 100%; margin-bottom: 16px; border-collapse: collapse; }
                .kpi-box { padding: 8px 12px; background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 6px; text-align: center; }
                .kpi-label { font-size: 8.5pt; color: #64748B; text-transform: uppercase; font-weight: 600; margin-bottom: 2px; }
                .kpi-val { font-size: 13pt; font-weight: bold; color: #0F172A; }
                .meta-table { width: 100%; margin-bottom: 16px; border-collapse: collapse; background: #F8FAFC; border-radius: 6px; border: 1px solid #E2E8F0; }
                .meta-table td { padding: 6px 10px; font-size: 9.5pt; }
                .data-table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 9pt; }
                .data-table th { background-color: #0F172A; color: #FFFFFF; font-weight: 600; padding: 7px 4px; border: 1px solid #334155; text-align: center; }
                .data-table td { padding: 5px 4px; border: 1px solid #CBD5E1; text-align: center; }
                .data-table tr:nth-child(even) { background-color: #F8FAFC; }
                .data-table td.left { text-align: left; padding-left: 8px; }
                .badge-p { background-color: #DCFCE7; color: #15803D; font-weight: bold; padding: 2px 5px; border-radius: 4px; font-size: 8.5pt; }
                .badge-a { background-color: #FEE2E2; color: #B91C1C; font-weight: bold; padding: 2px 5px; border-radius: 4px; font-size: 8.5pt; }
                .badge-l { background-color: #FEF3C7; color: #B45309; font-weight: bold; padding: 2px 5px; border-radius: 4px; font-size: 8.5pt; }
                .badge-eligible { background-color: #DCFCE7; color: #166534; font-weight: bold; padding: 3px 6px; border-radius: 4px; font-size: 8pt; display: inline-block; }
                .badge-shortage { background-color: #FEE2E2; color: #991B1B; font-weight: bold; padding: 3px 6px; border-radius: 4px; font-size: 8pt; display: inline-block; }
                .summary-row { background-color: #E2E8F0; font-weight: bold; }
                .footer-signatures { width: 100%; margin-top: 36px; border-collapse: collapse; }
                .footer-signatures td { padding: 10px; font-size: 9.5pt; vertical-align: top; }
                .sign-line { border-bottom: 1.5px solid #64748B; width: 180px; margin-bottom: 6px; }
            </style>
            </head>
            <body>
                <div class='header-banner'>
                    <h1>${escapeCsv(options.institutionName)}</h1>
                    <h2>Official Academic Attendance Register &mdash; Session ${LocalDate.now().year}-${LocalDate.now().year + 1}</h2>
                </div>
                
                <table class='meta-table'>
                    <tr>
                        <td width='50%'><b>Course Title:</b> ${escapeCsv(course?.name ?: "Course")} (<b>${escapeCsv(course?.code ?: "N/A")}</b>)</td>
                        <td width='50%'><b>Duration:</b> ${options.startDate} to ${options.endDate}</td>
                    </tr>
                    <tr>
                        <td><b>Faculty In-Charge:</b> ${escapeCsv(options.facultyName)}</td>
                        <td><b>Semester / Section:</b> ${escapeCsv(course?.semester ?: "Sem")} ${escapeCsv(course?.section ?: "Section")}</td>
                    </tr>
                    <tr>
                        <td><b>Total Lectures Conducted:</b> ${totalClasses}</td>
                        <td><b>Class Attendance Average:</b> <b>${String.format(Locale.ENGLISH, "%.1f%%", classAvgPct)}</b></td>
                    </tr>
                    <tr>
                        <td><b>Eligible Students (&ge;75%):</b> <span class='badge-eligible'>$eligibleCount / ${students.size}</span></td>
                        <td><b>Shortage Defaulters (&lt;75%):</b> <span class='badge-shortage'>$shortageCount / ${students.size}</span></td>
                    </tr>
                </table>

                <table class='data-table'>
                    <thead>
                        <tr>
                            <th style='width:28px'>#</th>
                            <th style='width:65px'>Roll No</th>
                            <th class='left' style='width:130px'>Student Name</th>
        """.trimIndent())

        dates.forEach { d ->
            sb.append("<th style='min-width:32px'>${formatShortDate(d)}</th>")
        }
        sb.append("<th style='width:32px'>Held</th><th style='width:28px'>P</th><th style='width:28px'>A</th><th style='width:28px'>L</th><th style='width:45px'>%</th><th style='width:75px'>Status</th></tr></thead><tbody>")

        studentStats.forEachIndexed { idx, row ->
            sb.append("<tr>")
            sb.append("<td>${idx + 1}</td>")
            sb.append("<td><b>${escapeCsv(row.student.rollNumber)}</b></td>")
            sb.append("<td class='left'>${escapeCsv(row.student.name)}</td>")

            dates.forEach { d ->
                val status = lookup[d]?.get(row.student.id)?.uppercase() ?: "-"
                when (status) {
                    "P", "PRESENT" -> sb.append("<td><span class='badge-p'>P</span></td>")
                    "A", "ABSENT" -> sb.append("<td><span class='badge-a'>A</span></td>")
                    "L", "LATE" -> sb.append("<td><span class='badge-l'>L</span></td>")
                    else -> sb.append("<td style='color:#94A3B8'>-</td>")
                }
            }

            sb.append("<td>$totalClasses</td>")
            sb.append("<td style='font-weight:600; color:#15803D'>${row.present}</td>")
            sb.append("<td style='font-weight:600; color:#B91C1C'>${row.absent}</td>")
            sb.append("<td style='font-weight:600; color:#B45309'>${row.late}</td>")

            val pctColor = if (row.percentage >= 75.0) "#15803D" else if (row.percentage >= 65.0) "#D97706" else "#B91C1C"
            sb.append("<td style='color:$pctColor; font-weight:bold'>${String.format(Locale.ENGLISH, "%.1f%%", row.percentage)}</td>")

            if (row.percentage >= 75.0) {
                sb.append("<td><span class='badge-eligible'>ELIGIBLE</span></td>")
            } else {
                sb.append("<td><span class='badge-shortage'>SHORTAGE</span></td>")
            }
            sb.append("</tr>")
        }

        // Daily Bottom Totals
        sb.append("<tr class='summary-row'><td colspan='3' style='text-align:right; padding-right:8px;'><b>TOTAL PRESENT (P)</b></td>")
        dates.forEach { d ->
            val pCount = students.count { s ->
                val st = lookup[d]?.get(s.id)?.uppercase()
                st == "P" || st == "PRESENT" || st == "L" || st == "LATE"
            }
            sb.append("<td><b>$pCount</b></td>")
        }
        sb.append("<td>$totalPossibleSlots</td><td>$grandTotalPresent</td><td>$grandTotalAbsent</td><td>$grandTotalLate</td><td>${String.format(Locale.ENGLISH, "%.1f%%", classAvgPct)}</td><td>-</td></tr>")

        sb.append("""
                    </tbody>
                </table>

                <table class='footer-signatures'>
                    <tr>
                        <td width='33%'>
                            <div class='sign-line'></div>
                            <b>Course Instructor / Faculty</b><br>
                            ${escapeCsv(options.facultyName)}<br>
                            Date: ${LocalDate.now()}
                        </td>
                        <td width='33%' style='text-align:center;'>
                            <div style='border:1.5px dashed #94A3B8; width:90px; height:60px; margin:0 auto; line-height:60px; color:#94A3B8; font-size:8pt;'>
                                [ DEPARTMENT SEAL ]
                            </div>
                        </td>
                        <td width='33%' style='text-align:right;'>
                            <div class='sign-line' style='margin-left:auto;'></div>
                            <b>Head of Department / Dean</b><br>
                            Academic Affairs<br>
                            Date: ____________
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent())

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write(sb.toString())
                writer.flush()
            }
        }
    }

    // =========================================================================
    // 3. PDF GENERATOR (Native Android PdfDocument - Landscape A4)
    // =========================================================================
    private fun generatePdf(
        file: File,
        course: CourseEntity?,
        students: List<StudentEntity>,
        dates: List<String>,
        lookup: Map<String, Map<String, String>>,
        options: ExportOptions
    ) {
        val pdfDoc = PdfDocument()

        // Landscape A4: 842 pt width x 595 pt height
        val pageWidth = 842
        val pageHeight = 595
        val marginLeft = 36f
        val marginRight = 36f
        val marginTop = 36f
        val marginBottom = 40f
        val printableWidth = pageWidth - marginLeft - marginRight

        // Paints
        val titlePaint = Paint().apply {
            color = Color.rgb(30, 58, 138)
            textSize = 15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val subTitlePaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerBgPaint = Paint().apply {
            color = Color.rgb(30, 41, 59) // Dark Slate
        }

        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val rowTextPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val presentPaint = Paint().apply {
            color = Color.rgb(22, 163, 74) // Green
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val absentPaint = Paint().apply {
            color = Color.rgb(220, 38, 38) // Red
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val latePaint = Paint().apply {
            color = Color.rgb(217, 119, 6) // Amber
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val dashPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val borderPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }

        val zebraBgPaint = Paint().apply {
            color = Color.rgb(248, 250, 252)
        }

        // Column widths setup
        val colSNoWidth = 24f
        val colRollWidth = 58f
        val colNameWidth = 100f
        val colPWidth = 22f
        val colAWidth = 22f
        val colPctWidth = 32f

        val fixedWidths = colSNoWidth + colRollWidth + colNameWidth + colPWidth + colAWidth + colPctWidth
        val remainingWidth = printableWidth - fixedWidths

        // Max dates that fit across the page without crowding
        val maxDatesPerPage = min(dates.size, 25)
        val visibleDates = dates.takeLast(maxDatesPerPage)
        val colDateWidth = remainingWidth / visibleDates.size.coerceAtLeast(1)

        val rowHeight = 16f
        val headerRowHeight = 20f

        // Pagination calculations
        val rowsPerPage = 22
        val totalPages = ((students.size - 1) / rowsPerPage) + 1

        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            var currentY = marginTop

            // 1. Header Banner
            canvas.drawText(options.institutionName, marginLeft, currentY, titlePaint)
            currentY += 14f

            val courseLabel = "Attendance Register: ${course?.name ?: ""} (${course?.code ?: ""}) · Range: ${options.startDate} to ${options.endDate}"
            canvas.drawText(courseLabel, marginLeft, currentY, subTitlePaint)

            // Right-aligned page indicator
            val pageStr = "Page ${pageIndex + 1} of $totalPages"
            val pageStrWidth = subTitlePaint.measureText(pageStr)
            canvas.drawText(pageStr, pageWidth - marginRight - pageStrWidth, currentY, subTitlePaint)
            currentY += 16f

            // Faculty & Count info
            val facultyInfo = "Faculty: ${options.facultyName}  |  Total Students: ${students.size}  |  Generated: ${LocalDate.now()}"
            canvas.drawText(facultyInfo, marginLeft, currentY, metaPaint)
            currentY += 12f

            // 2. Table Header
            val headerTop = currentY
            val headerBottom = currentY + headerRowHeight
            canvas.drawRect(marginLeft, headerTop, marginLeft + printableWidth, headerBottom, headerBgPaint)

            var colX = marginLeft

            // Draw S.No header
            canvas.drawText("#", colX + (colSNoWidth / 2f), headerTop + 13f, headerTextPaint)
            colX += colSNoWidth

            // Draw Roll header
            canvas.drawText("Roll No", colX + (colRollWidth / 2f), headerTop + 13f, headerTextPaint)
            colX += colRollWidth

            // Draw Name header
            val namePaintHeader = Paint(headerTextPaint).apply { textAlign = Paint.Align.LEFT }
            canvas.drawText(" Student Name", colX + 4f, headerTop + 13f, namePaintHeader)
            colX += colNameWidth

            // Draw Date headers
            visibleDates.forEach { date ->
                val shortLabel = formatShortDate(date)
                canvas.drawText(shortLabel, colX + (colDateWidth / 2f), headerTop + 13f, headerTextPaint)
                colX += colDateWidth
            }

            // Summary columns
            canvas.drawText("P", colX + (colPWidth / 2f), headerTop + 13f, headerTextPaint)
            colX += colPWidth

            canvas.drawText("A", colX + (colAWidth / 2f), headerTop + 13f, headerTextPaint)
            colX += colAWidth

            canvas.drawText("%", colX + (colPctWidth / 2f), headerTop + 13f, headerTextPaint)

            currentY = headerBottom

            // 3. Table Rows
            val startIndex = pageIndex * rowsPerPage
            val endIndex = min(startIndex + rowsPerPage, students.size)

            for (i in startIndex until endIndex) {
                val student = students[i]
                val rowTop = currentY
                val rowBottom = currentY + rowHeight

                // Alternate row background
                if (i % 2 == 1) {
                    canvas.drawRect(marginLeft, rowTop, marginLeft + printableWidth, rowBottom, zebraBgPaint)
                }

                // Row border
                canvas.drawRect(marginLeft, rowTop, marginLeft + printableWidth, rowBottom, borderPaint)

                var cellX = marginLeft

                // S.No
                val centerRowPaint = Paint(rowTextPaint).apply { textAlign = Paint.Align.CENTER }
                canvas.drawText("${i + 1}", cellX + (colSNoWidth / 2f), rowTop + 11.5f, centerRowPaint)
                cellX += colSNoWidth

                // Roll No
                val boldRowPaint = Paint(rowTextPaint).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
                val rollStr = if (student.rollNumber.length > 10) student.rollNumber.take(9) + "…" else student.rollNumber
                canvas.drawText(rollStr, cellX + 3f, rowTop + 11.5f, boldRowPaint)
                cellX += colRollWidth

                // Name
                val nameStr = if (student.name.length > 17) student.name.take(16) + "…" else student.name
                canvas.drawText(nameStr, cellX + 3f, rowTop + 11.5f, rowTextPaint)
                cellX += colNameWidth

                // Attendance Marks
                var pCount = 0
                var aCount = 0

                visibleDates.forEach { date ->
                    val status = lookup[date]?.get(student.id) ?: "-"
                    when (status.uppercase()) {
                        "P", "PRESENT" -> {
                            pCount++
                            canvas.drawText("P", cellX + (colDateWidth / 2f), rowTop + 11.5f, presentPaint)
                        }
                        "A", "ABSENT" -> {
                            aCount++
                            canvas.drawText("A", cellX + (colDateWidth / 2f), rowTop + 11.5f, absentPaint)
                        }
                        "L", "LATE" -> {
                            pCount++
                            canvas.drawText("L", cellX + (colDateWidth / 2f), rowTop + 11.5f, latePaint)
                        }
                        else -> {
                            canvas.drawText("-", cellX + (colDateWidth / 2f), rowTop + 11.5f, dashPaint)
                        }
                    }
                    cellX += colDateWidth
                }

                // P Total
                canvas.drawText("$pCount", cellX + (colPWidth / 2f), rowTop + 11.5f, centerRowPaint)
                cellX += colPWidth

                // A Total
                canvas.drawText("$aCount", cellX + (colAWidth / 2f), rowTop + 11.5f, centerRowPaint)
                cellX += colAWidth

                // Pct
                val pct = if (visibleDates.isNotEmpty()) (pCount * 100.0 / visibleDates.size) else 0.0
                val pctPaint = Paint(centerRowPaint).apply {
                    color = if (pct >= 75.0) Color.rgb(22, 163, 74) else if (pct >= 60.0) Color.rgb(217, 119, 6) else Color.rgb(220, 38, 38)
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                canvas.drawText("${pct.toInt()}%", cellX + (colPctWidth / 2f), rowTop + 11.5f, pctPaint)

                currentY = rowBottom
            }

            // 4. Footer & Signatures on the last page
            if (pageIndex == totalPages - 1) {
                val sigY = pageHeight - marginBottom - 12f
                val linePaint = Paint().apply {
                    color = Color.rgb(100, 116, 139)
                    strokeWidth = 1f
                }

                // Left: Faculty Signature
                canvas.drawLine(marginLeft, sigY, marginLeft + 150f, sigY, linePaint)
                canvas.drawText("Faculty Signature: ${options.facultyName}", marginLeft, sigY + 14f, subTitlePaint)

                // Right: HOD Signature
                val hodX = pageWidth - marginRight - 150f
                canvas.drawLine(hodX, sigY, hodX + 150f, sigY, linePaint)
                canvas.drawText("Head of Department Signature", hodX, sigY + 14f, subTitlePaint)
            }

            pdfDoc.finishPage(page)
        }

        FileOutputStream(file).use { fos ->
            pdfDoc.writeTo(fos)
        }
        pdfDoc.close()
    }

    private fun escapeCsv(text: String): String {
        return text.replace("\"", "\"\"").replace("\n", " ")
    }

    private fun formatShortDate(dateStr: String): String {
        return try {
            val d = LocalDate.parse(dateStr)
            d.format(DateTimeFormatter.ofPattern("dd/MM (EEE)"))
        } catch (_: Exception) {
            if (dateStr.length >= 5) dateStr.takeLast(5) else dateStr
        }
    }
}
