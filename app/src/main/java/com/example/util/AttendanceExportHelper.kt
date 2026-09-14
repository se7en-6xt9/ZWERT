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
    // 1. EXCEL / CSV GENERATOR
    // =========================================================================
    private fun generateCsvExcel(
        file: File,
        course: CourseEntity?,
        students: List<StudentEntity>,
        dates: List<String>,
        lookup: Map<String, Map<String, String>>,
        options: ExportOptions
    ) {
        FileOutputStream(file).use { fos ->
            // Write UTF-8 BOM so Excel opens it with proper UTF-8 decoding
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                // Institution & Course Headers
                writer.append("\"${escapeCsv(options.institutionName)}\"\n")
                writer.append("\"Attendance Register: ${escapeCsv(course?.name ?: "")} (${escapeCsv(course?.code ?: "")})\"\n")
                writer.append("\"Faculty: ${escapeCsv(options.facultyName)}\"\n")
                writer.append("\"Date Range: ${options.startDate} to ${options.endDate}\"\n")
                writer.append("\"Generated: ${LocalDate.now()}\"\n\n")

                // Table Header Row
                writer.append("\"S.No.\",\"Roll Number\",\"Student Name\"")
                dates.forEach { date ->
                    writer.append(",\"${formatShortDate(date)}\"")
                }
                writer.append(",\"Total Present\",\"Total Absent\",\"Total Classes\",\"Attendance %\"\n")

                // Student Rows
                var grandTotalPresent = 0
                var grandTotalPossible = 0

                students.forEachIndexed { index, student ->
                    writer.append("\"${index + 1}\",\"${escapeCsv(student.rollNumber)}\",\"${escapeCsv(student.name)}\"")

                    var presentCount = 0
                    var absentCount = 0
                    var markedCount = 0

                    dates.forEach { date ->
                        val status = lookup[date]?.get(student.id) ?: "-"
                        writer.append(",\"$status\"")
                        when (status.uppercase()) {
                            "P", "PRESENT" -> {
                                presentCount++
                                markedCount++
                            }
                            "L", "LATE" -> {
                                presentCount++ // Count as present
                                markedCount++
                            }
                            "A", "ABSENT" -> {
                                absentCount++
                                markedCount++
                            }
                        }
                    }

                    val totalClasses = dates.size
                    val pct = if (totalClasses > 0) (presentCount * 100.0 / totalClasses) else 0.0
                    grandTotalPresent += presentCount
                    grandTotalPossible += totalClasses

                    writer.append(",\"$presentCount\",\"$absentCount\",\"$totalClasses\",\"${String.format(Locale.ENGLISH, "%.1f%%", pct)}\"\n")
                }

                // Summary Row
                writer.append("\n\"SUMMARY\",\"Class Average\",\"-\"")
                dates.forEach { _ -> writer.append(",\"-\"") }
                val classAvgPct = if (grandTotalPossible > 0) (grandTotalPresent * 100.0 / grandTotalPossible) else 0.0
                writer.append(",\"-\",\"-\",\"-\",\"${String.format(Locale.ENGLISH, "%.1f%%", classAvgPct)}\"\n")
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
        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>
            <head>
            <meta charset='utf-8'>
            <title>Attendance Register</title>
            <style>
                body { font-family: 'Calibri', 'Arial', sans-serif; font-size: 11pt; color: #1E293B; margin: 20px; }
                h1 { font-size: 18pt; color: #1E3A8A; margin-bottom: 4px; text-align: center; }
                h2 { font-size: 13pt; color: #475569; margin-top: 0; text-align: center; font-weight: normal; }
                .meta-table { width: 100%; margin-bottom: 18px; border-collapse: collapse; }
                .meta-table td { padding: 5px 8px; font-size: 10pt; }
                .data-table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 9.5pt; }
                .data-table th { background-color: #1E293B; color: #FFFFFF; font-weight: bold; padding: 7px 5px; border: 1px solid #CBD5E1; text-align: center; }
                .data-table td { padding: 6px 5px; border: 1px solid #E2E8F0; text-align: center; }
                .data-table tr:nth-child(even) { background-color: #F8FAFC; }
                .data-table td.left { text-align: left; }
                .tag-p { color: #16A34A; font-weight: bold; }
                .tag-a { color: #DC2626; font-weight: bold; }
                .tag-l { color: #D97706; font-weight: bold; }
                .footer { margin-top: 35px; width: 100%; font-size: 10pt; }
            </style>
            </head>
            <body>
                <h1>${options.institutionName}</h1>
                <h2>Official Attendance Register</h2>
                
                <table class='meta-table'>
                    <tr>
                        <td><b>Course:</b> ${course?.name ?: "N/A"} (${course?.code ?: ""})</td>
                        <td style='text-align:right'><b>Date Range:</b> ${options.startDate} to ${options.endDate}</td>
                    </tr>
                    <tr>
                        <td><b>Faculty:</b> ${options.facultyName}</td>
                        <td style='text-align:right'><b>Total Students:</b> ${students.size}</td>
                    </tr>
                </table>

                <table class='data-table'>
                    <thead>
                        <tr>
                            <th style='width:30px'>#</th>
                            <th style='width:75px'>Roll No</th>
                            <th class='left' style='width:160px'>Student Name</th>
        """.trimIndent())

        dates.forEach { d ->
            sb.append("<th>${formatShortDate(d)}</th>")
        }
        sb.append("<th>P</th><th>A</th><th>Total</th><th>%</th></tr></thead><tbody>")

        var grandPresent = 0
        var grandTotal = 0

        students.forEachIndexed { idx, s ->
            sb.append("<tr>")
            sb.append("<td>${idx + 1}</td>")
            sb.append("<td><b>${s.rollNumber}</b></td>")
            sb.append("<td class='left'>${s.name}</td>")

            var pCount = 0
            var aCount = 0

            dates.forEach { d ->
                val status = lookup[d]?.get(s.id) ?: "-"
                when (status.uppercase()) {
                    "P", "PRESENT" -> {
                        pCount++
                        sb.append("<td class='tag-p'>P</td>")
                    }
                    "A", "ABSENT" -> {
                        aCount++
                        sb.append("<td class='tag-a'>A</td>")
                    }
                    "L", "LATE" -> {
                        pCount++
                        sb.append("<td class='tag-l'>L</td>")
                    }
                    else -> sb.append("<td>-</td>")
                }
            }

            val totalClasses = dates.size
            val pct = if (totalClasses > 0) (pCount * 100.0 / totalClasses) else 0.0
            grandPresent += pCount
            grandTotal += totalClasses

            sb.append("<td><b>$pCount</b></td>")
            sb.append("<td>$aCount</td>")
            sb.append("<td>$totalClasses</td>")
            val pctColor = if (pct >= 75.0) "#16A34A" else if (pct >= 60.0) "#D97706" else "#DC2626"
            sb.append("<td style='color:$pctColor; font-weight:bold'>${String.format(Locale.ENGLISH, "%.1f%%", pct)}</td>")
            sb.append("</tr>")
        }

        val overallAvg = if (grandTotal > 0) (grandPresent * 100.0 / grandTotal) else 0.0

        sb.append("""
                    </tbody>
                </table>

                <br>
                <table class='meta-table' style='margin-top:15px; background-color:#F1F5F9; padding:8px;'>
                    <tr>
                        <td><b>Class Overall Attendance:</b> ${String.format(Locale.ENGLISH, "%.1f%%", overallAvg)}</td>
                        <td style='text-align:right'><b>Report Generated:</b> ${LocalDate.now()}</td>
                    </tr>
                </table>

                <table class='footer'>
                    <tr>
                        <td style='padding-top:40px;'>____________________________<br><b>Faculty Signature</b></td>
                        <td style='padding-top:40px; text-align:right;'>____________________________<br><b>Head of Department / Dean</b></td>
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
            d.format(DateTimeFormatter.ofPattern("dd/MM"))
        } catch (_: Exception) {
            if (dateStr.length >= 5) dateStr.takeLast(5) else dateStr
        }
    }
}
