from pathlib import Path

path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/ui/AttendanceScreen.kt")
text = path.read_text()

old_call = '''        SimplePayrollReceiptDialog(\n            entry = entry,\n            onDismiss = { receiptEntry = null }\n        )'''
new_call = '''        SimplePayrollReceiptDialog(\n            entry = entry,\n            state = state,\n            onDismiss = { receiptEntry = null }\n        )'''
if old_call not in text:
    raise SystemExit("Could not find payroll receipt dialog call to patch")
text = text.replace(old_call, new_call, 1)

start_marker = '@Composable\nprivate fun SimplePayrollReceiptDialog('
end_marker = '@Composable\nprivate fun ReceiptLine('
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit("Could not find payroll receipt dialog block to patch")

new_dialog = '''@Composable
private fun SimplePayrollReceiptDialog(
    entry: PayrollPaymentEntity,
    state: PixnetState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val date = LocalDate.ofEpochDay(entry.dateEpochDay)
    val (periodStart, periodEnd) = payrollPeriodBounds(date)
    val attendanceDates = state.attendance
        .filter {
            if (it.staff != entry.staff) return@filter false
            val attendanceDate = LocalDate.ofEpochDay(it.dateEpochDay)
            !attendanceDate.isBefore(periodStart) &&
                !attendanceDate.isAfter(minOf(periodEnd, date))
        }
        .map { LocalDate.ofEpochDay(it.dateEpochDay) }
        .sorted()

    val receipt = remember(
        entry.id,
        entry.dateEpochDay,
        entry.staff,
        entry.amount,
        attendanceDates
    ) {
        PayrollReceiptData(
            staff = entry.staff,
            datePaid = date,
            amountPaid = entry.amount,
            attendanceDates = attendanceDates
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payroll Receipt") },
        text = {
            Card {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("PIXNET", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Payroll Receipt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    HorizontalDivider()
                    ReceiptLine("Staff", receipt.staff)
                    ReceiptLine("Payroll Period", receipt.payrollPeriodLabel)
                    ReceiptLine(
                        "Days Worked",
                        "${receipt.daysWorked} day${if (receipt.daysWorked == 1) "" else "s"}"
                    )
                    Text(
                        "Attendance Record",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (receipt.attendanceDates.isEmpty()) {
                            "No attendance dates recorded for this payroll period."
                        } else {
                            receipt.attendanceDates.joinToString(", ") { formatDate(it) }
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    ReceiptLine("Date Paid", formatDate(receipt.datePaid))
                    ReceiptLine("Amount Paid", money(receipt.amountPaid), bold = true)
                    HorizontalDivider()
                    Text(
                        "Received payroll payment from PIXNET.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { PayrollReceiptUtils.shareReceipt(context, receipt) }) {
                Text("Share Receipt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

'''

text = text[:start] + new_dialog + text[end:]
path.write_text(text)
print("AttendanceScreen payroll receipt patched for attendance day records")
