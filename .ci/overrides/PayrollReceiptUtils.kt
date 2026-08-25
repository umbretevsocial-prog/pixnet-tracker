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

data class PayrollReceiptData(
    val staff: String,
    val datePaid: LocalDate,
    val payrollPeriodLabel: String,
    val amountPaid: Double
)

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
        val height = 1350
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

        canvas.drawText("PIXNET", left, y, titlePaint)
        y += 78f
        canvas.drawText("Payroll Receipt", left, y, subtitlePaint)
        y += 40f
        canvas.drawLine(left, y, width - left, y, linePaint)
        y += 90f

        canvas.drawText("Staff", left, y, labelPaint)
        canvas.drawText(data.staff, valueX, y, valuePaint)
        y += 95f

        canvas.drawText("Date Paid", left, y, labelPaint)
        canvas.drawText(formatDate(data.datePaid), valueX, y, valuePaint)
        y += 95f

        canvas.drawText("Payroll Period", left, y, labelPaint)
        canvas.drawText(data.payrollPeriodLabel, valueX, y, valuePaint)
        y += 95f

        canvas.drawText("Amount Paid", left, y, labelPaint)
        canvas.drawText(money(data.amountPaid), valueX, y, amountPaint)
        y += 120f

        canvas.drawLine(left, y, width - left, y, linePaint)
        y += 80f
        canvas.drawText("Received payroll payment from PIXNET.", left, y, notePaint)
        y += 55f
        canvas.drawText("This receipt is system-generated for staff payroll record purposes.", left, y, notePaint)

        return bitmap
    }
}
