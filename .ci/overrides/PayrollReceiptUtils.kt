package com.pixnet.tracker.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PayrollReceiptData(
    val staff: String,
    val datePaid: LocalDate,
    val amountPaid: Double,
    val attendanceDates: List<LocalDate>
) {
    val payrollPeriodLabel: String = payrollPeriodLabelFor(datePaid)
    val daysWorked: Int = attendanceDates.size
}

fun payrollPeriodBounds(date: LocalDate): Pair<LocalDate, LocalDate> {
    return if (date.dayOfMonth <= 15) {
        date.withDayOfMonth(1) to date.withDayOfMonth(15)
    } else {
        date.withDayOfMonth(16) to date.withDayOfMonth(date.lengthOfMonth())
    }
}

private fun payrollPeriodLabelFor(date: LocalDate): String {
    val (start, end) = payrollPeriodBounds(date)
    return "${formatDate(start)} – ${formatDate(end)}"
}

object PayrollReceiptUtils {
    fun shareReceipt(context: Context, data: PayrollReceiptData) {
        val uri = createReceiptImageUri(context, data)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${data.staff} Payroll Receipt")
            putExtra(Intent.EXTRA_TEXT, "Payroll receipt for ${data.staff} - ${money(data.amountPaid)}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share payroll receipt"))
    }

    fun createReceiptImageUri(context: Context, data: PayrollReceiptData): Uri {
        val bitmap = buildReceiptBitmap(data)
        val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(
            receiptsDir,
            "payroll_receipt_${data.staff.lowercase()}_${data.datePaid}.png"
        )
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun buildReceiptBitmap(data: PayrollReceiptData): Bitmap {
        val width = 1080
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B7280")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 40f
        }
        val recordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 36f
        }
        val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#14532D")
            textSize = 66f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#374151")
            textSize = 32f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5E7EB")
            strokeWidth = 3f
        }

        var y = 120f
        val left = 90f
        val valueX = 350f
        val right = width - 90f

        canvas.drawText("PIXNET", left, y, titlePaint)
        y += 78f
        canvas.drawText("Payroll Receipt", left, y, subtitlePaint)
        y += 40f
        canvas.drawLine(left, y, right, y, linePaint)
        y += 90f

        canvas.drawText("Staff", left, y, labelPaint)
        canvas.drawText(data.staff, valueX, y, valuePaint)
        y += 95f

        canvas.drawText("Payroll Period", left, y, labelPaint)
        canvas.drawText(data.payrollPeriodLabel, valueX, y, valuePaint)
        y += 95f

        canvas.drawText("Days Worked", left, y, labelPaint)
        val dayLabel = "${data.daysWorked} day${if (data.daysWorked == 1) "" else "s"}"
        canvas.drawText(dayLabel, valueX, y, valuePaint)
        y += 82f

        canvas.drawText("Attendance Record", left, y, labelPaint)
        y += 55f

        val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val attendanceText = if (data.attendanceDates.isEmpty()) {
            "No attendance dates recorded."
        } else {
            data.attendanceDates.joinToString(", ") { it.format(shortDate) }
        }
        y = drawWrappedText(
            canvas = canvas,
            text = attendanceText,
            x = left,
            startY = y,
            maxWidth = right - left,
            paint = recordPaint,
            lineHeight = 52f
        )
        y += 45f

        canvas.drawText("Date Paid", left, y, labelPaint)
        canvas.drawText(formatDate(data.datePaid), valueX, y, valuePaint)
        y += 105f

        canvas.drawText("Amount Paid", left, y, labelPaint)
        canvas.drawText(money(data.amountPaid), valueX, y, amountPaint)
        y += 120f

        canvas.drawLine(left, y, right, y, linePaint)
        y += 80f
        canvas.drawText("Received payroll payment from PIXNET.", left, y, notePaint)

        return bitmap
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float
    ): Float {
        val words = text.split(" ")
        var line = ""
        var y = startY

        words.forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth) {
                line = candidate
            } else {
                if (line.isNotEmpty()) {
                    canvas.drawText(line, x, y, paint)
                    y += lineHeight
                }
                line = word
            }
        }

        if (line.isNotEmpty()) {
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }
        return y
    }
}
