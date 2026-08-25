package com.pixnet.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixnet.tracker.data.AttendanceEntity
import com.pixnet.tracker.data.PayrollPaymentEntity
import com.pixnet.tracker.model.PixnetRules
import com.pixnet.tracker.model.PixnetState
import com.pixnet.tracker.model.StaffPayrollSummary
import java.time.LocalDate
import kotlin.math.max

@Composable
fun AttendanceScreen(
    state: PixnetState,
    viewModel: PixnetViewModel,
    modifier: Modifier = Modifier
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showPayroll by remember { mutableStateOf(false) }

    val selectedEntry = state.attendance.firstOrNull {
        it.dateEpochDay == selectedDate.toEpochDay()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                "Attendance & Payroll",
                "Attendance creates salary earned. Salary payments are tracked separately for each staff member.",
                trailing = {
                    Button(onClick = { showPayroll = true }) {
                        Text("Pay Staff")
                    }
                }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    "Total Payroll Outstanding",
                    money(state.outstandingPayroll),
                    Modifier.weight(1f)
                )
                StatCard(
                    "Owner Advance",
                    money(state.currentOwnerAdvance),
                    Modifier.weight(1f)
                )
            }
        }

        item {
            SectionHeader(
                "Staff Payroll Summary",
                "Each employee has their own Earned, Paid, and Balance. Payments cannot reduce another employee's balance."
            )
        }

        if (state.staffPayroll.isEmpty()) {
            item { EmptyState("No payroll summary available yet.") }
        } else {
            items(state.staffPayroll, key = { it.name }) { summary ->
                StaffPayrollCard(summary)
            }
        }

        item {
            Card {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Set Attendance", fontWeight = FontWeight.Bold)
                    DateButton("Date", selectedDate, { selectedDate = it })

                    Text(
                        "Current: ${selectedEntry?.staff ?: "Not set"}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = { viewModel.setAttendance(selectedDate, "Mayanne") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mayanne • ${money(PixnetRules.STAFF_RATE)}")
                    }

                    Button(
                        onClick = { viewModel.setAttendance(selectedDate, "Hannah") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hannah • ${money(PixnetRules.STAFF_RATE)}")
                    }

                    OutlinedButton(
                        onClick = { viewModel.setAttendance(selectedDate, "SHOP CLOSED") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SHOP CLOSED")
                    }

                    if (selectedEntry != null) {
                        TextButton(
                            onClick = { viewModel.clearAttendance(selectedDate) }
                        ) {
                            Text("Clear this date")
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Recent Attendance")
        }

        if (state.attendance.isEmpty()) {
            item { EmptyState("No attendance recorded yet.") }
        } else {
            items(state.attendance.take(30), key = { it.dateEpochDay }) { entry ->
                AttendanceRow(entry)
            }
        }

        item {
            SectionHeader(
                "Recent Salary Payments",
                "Payments are attached to the selected employee and add to Owner Advance."
            )
        }

        if (state.payrollPayments.isEmpty()) {
            item { EmptyState("No salary payments recorded yet.") }
        } else {
            items(state.payrollPayments.take(20), key = { it.id }) { entry ->
                PayrollRow(entry, onDelete = { viewModel.deletePayrollPayment(entry) })
            }
        }
    }

    if (showPayroll) {
        AddPayrollDialog(
            state = state,
            onDismiss = { showPayroll = false },
            onSave = { date, staff, amount, reference ->
                val saved = viewModel.addPayrollPayment(date, staff, amount, reference)
                if (saved) showPayroll = false
                saved
            }
        )
    }
}

@Composable
private fun StaffPayrollCard(summary: StaffPayrollSummary) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(summary.name, fontWeight = FontWeight.Bold)
                    Text(
                        "${summary.daysWorked} day${if (summary.daysWorked == 1) "" else "s"} worked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(if (summary.outstandingBalance <= 0.005) "SETTLED" else "OUTSTANDING")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniPayrollStat("Earned", summary.salaryEarned, Modifier.weight(1f))
                MiniPayrollStat("Paid", summary.salaryPaid, Modifier.weight(1f))
                MiniPayrollStat("Balance", summary.outstandingBalance, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniPayrollStat(label: String, value: Double, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(money(value), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AttendanceRow(entry: AttendanceEntity) {
    val date = LocalDate.ofEpochDay(entry.dateEpochDay)
    val salary = PixnetRules.staffSalary(entry.staff)

    Card {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(formatDate(date), fontWeight = FontWeight.Bold)
                Text(
                    PixnetRules.periodFor(date).name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(entry.staff, fontWeight = FontWeight.SemiBold)
                Text(if (salary > 0) money(salary) else "No salary")
            }
        }
    }
}

@Composable
private fun PayrollRow(
    entry: PayrollPaymentEntity,
    onDelete: () -> Unit
) {
    val date = LocalDate.ofEpochDay(entry.dateEpochDay)
    Card {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(entry.staff, fontWeight = FontWeight.Bold)
                    Text(formatDate(date), style = MaterialTheme.typography.bodySmall)
                }
                Text(money(entry.amount), fontWeight = FontWeight.Bold)
            }
            if (entry.reference.isNotBlank()) {
                Text(
                    entry.reference,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

private data class PayrollAsOf(
    val earned: Double,
    val paid: Double,
    val balance: Double,
    val daysWorked: Int
)

private fun payrollAsOf(
    state: PixnetState,
    staff: String,
    date: LocalDate
): PayrollAsOf {
    val attendanceRows = state.attendance.filter {
        it.staff == staff && !LocalDate.ofEpochDay(it.dateEpochDay).isAfter(date)
    }
    val earned = attendanceRows.sumOf { PixnetRules.staffSalary(it.staff) }
    val paid = state.payrollPayments.filter {
        it.staff == staff && !LocalDate.ofEpochDay(it.dateEpochDay).isAfter(date)
    }.sumOf { it.amount }
    return PayrollAsOf(
        earned = earned,
        paid = paid,
        balance = max(0.0, earned - paid),
        daysWorked = attendanceRows.size
    )
}

@Composable
private fun AddPayrollDialog(
    state: PixnetState,
    onDismiss: () -> Unit,
    onSave: (LocalDate, String, Double, String) -> Boolean
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var staff by remember { mutableStateOf("Mayanne") }
    var amountText by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val payroll = payrollAsOf(state, staff, date)
    val exceedsBalance = amount > payroll.balance + 0.005
    val canSave = amount > 0.0 && !exceedsBalance && payroll.balance > 0.005

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Salary Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateButton("Payment date", date, {
                    date = it
                    saveError = null
                })

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = staff == "Mayanne",
                        onClick = {
                            staff = "Mayanne"
                            amountText = ""
                            saveError = null
                        },
                        label = { Text("Mayanne") }
                    )
                    FilterChip(
                        selected = staff == "Hannah",
                        onClick = {
                            staff = "Hannah"
                            amountText = ""
                            saveError = null
                        },
                        label = { Text("Hannah") }
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("$staff as of ${formatDate(date)}", fontWeight = FontWeight.Bold)
                        Text("Days worked: ${payroll.daysWorked}")
                        Text("Salary earned: ${money(payroll.earned)}")
                        Text("Salary paid: ${money(payroll.paid)}")
                        Text(
                            "Balance: ${money(payroll.balance)}",
                            fontWeight = FontWeight.Bold,
                            color = if (payroll.balance > 0.005) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        saveError = null
                    },
                    label = { Text("Amount paid") },
                    prefix = { Text("₱") },
                    singleLine = true,
                    isError = exceedsBalance
                )

                if (payroll.balance > 0.005) {
                    TextButton(
                        onClick = {
                            amountText = String.format(java.util.Locale.US, "%.2f", payroll.balance)
                            saveError = null
                        }
                    ) {
                        Text("Pay full balance ${money(payroll.balance)}")
                    }
                }

                if (exceedsBalance) {
                    Text(
                        "Payment cannot exceed ${money(payroll.balance)} outstanding for $staff as of this date.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (payroll.balance <= 0.005) {
                    Text(
                        "$staff has no unpaid salary as of this date.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Reference / Notes") }
                )

                Text(
                    "Only this employee's payroll balance is reduced. The actual payment also becomes Owner Advance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                saveError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    val saved = onSave(date, staff, amount, reference)
                    if (!saved) {
                        saveError = "Payment was not saved because the payroll balance changed. Please check the balance and try again."
                    }
                }
            ) {
                Text("Save Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
