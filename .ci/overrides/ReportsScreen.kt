package com.pixnet.tracker.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixnet.tracker.data.ExpenseEntity
import com.pixnet.tracker.model.BusinessCalculator
import com.pixnet.tracker.model.OwnerSummary
import com.pixnet.tracker.model.PixnetRules
import com.pixnet.tracker.model.PixnetState
import java.time.LocalDate
import kotlin.math.abs

private enum class ReportMode { WEEKLY, CUTOFF }
private data class ReportPeriod(val label: String, val start: LocalDate, val end: LocalDate)

private data class SimpleReport(
    val period: ReportPeriod,
    val asOf: LocalDate,
    val isFinal: Boolean,
    val pisonet: Double,
    val printer: Double,
    val other: Double,
    val expenses: Double,
    val payroll: Double,
    val ownerContributions: Double,
    val ownerBalances: List<OwnerSummary>,
    val unpaidBills: Double,
    val unpaidPayroll: Double,
    val expenseRows: List<ExpenseEntity>
) {
    val collection: Double get() = pisonet + printer + other
    val totalCost: Double get() = expenses + payroll
    val sharedBalance: Double get() = totalCost - collection
    val sharePerOwner: Double get() = sharedBalance / PixnetRules.OWNERS.size
}

@Composable
fun ReportsScreen(state: PixnetState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val effective = minOf(today, PixnetRules.END_DATE)
    var mode by remember { mutableStateOf(ReportMode.WEEKLY) }

    val weeks = remember(effective) { weeklyPeriods(effective) }
    val cutoffs = remember { PixnetRules.PERIODS.map { ReportPeriod(it.name, it.start, it.end) } }
    val currentWeek = weeks.indexOfLast { !effective.isBefore(it.start) }.coerceAtLeast(0)
    val currentCutoff = cutoffs.indexOfLast { !effective.isBefore(it.start) }.coerceAtLeast(0)
    var weekIndex by remember { mutableIntStateOf(currentWeek) }
    var cutoffIndex by remember { mutableIntStateOf(currentCutoff) }

    val list = if (mode == ReportMode.WEEKLY) weeks else cutoffs
    val index = if (mode == ReportMode.WEEKLY) weekIndex else cutoffIndex
    val maxIndex = if (mode == ReportMode.WEEKLY) currentWeek else currentCutoff
    val period = list.getOrNull(index) ?: return
    val report = remember(state, period, today) { makeSimpleReport(state, period, today) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                "Business Reports",
                "Cutoffs and weeks are snapshots only. They never settle, repay, distribute, or reset money."
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == ReportMode.WEEKLY,
                    onClick = { mode = ReportMode.WEEKLY; weekIndex = currentWeek },
                    label = { Text("Weekly") }
                )
                FilterChip(
                    selected = mode == ReportMode.CUTOFF,
                    onClick = { mode = ReportMode.CUTOFF; cutoffIndex = currentCutoff },
                    label = { Text("Per Cutoff") }
                )
            }
        }

        item {
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (mode == ReportMode.WEEKLY) weekIndex-- else cutoffIndex-- },
                        enabled = index > 0
                    ) { Icon(Icons.Default.ChevronLeft, "Previous") }

                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(report.period.label, fontWeight = FontWeight.Bold)
                        Text(
                            "${formatDate(report.period.start)} - ${formatDate(report.period.end)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        StatusPill(if (report.isFinal) "FINAL" else "PROJECTED")
                    }

                    IconButton(
                        onClick = { if (mode == ReportMode.WEEKLY) weekIndex++ else cutoffIndex++ },
                        enabled = index < maxIndex
                    ) { Icon(Icons.Default.ChevronRight, "Next") }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "PIXNET ${report.period.label} Report")
                        putExtra(Intent.EXTRA_TEXT, reportText(report, mode))
                    }
                    context.startActivity(Intent.createChooser(intent, "Share PIXNET report"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Share Report")
            }
        }

        item { SectionHeader("Collections") }
        item { ReportPair("Pisonet", report.pisonet, "Printer", report.printer) }
        item { ReportPair("Other", report.other, "Total Collection", report.collection) }

        item { SectionHeader("Expenses & Payroll") }
        item { ReportPair("Expenses", report.expenses, "Payroll", report.payroll) }
        item { ReportPair("Total Cost", report.totalCost, "Owner Contributions", report.ownerContributions) }

        item { SectionHeader("Shared Result", "Formula: Expenses + Payroll − Collections. Then divide equally by 7 owners.") }
        item {
            ReportPair(
                if (report.sharedBalance >= 0) "Shared Balance" else "Business Surplus",
                abs(report.sharedBalance),
                if (report.sharePerOwner >= 0) "Share / Owner" else "Credit / Owner",
                abs(report.sharePerOwner),
                secondNegative = report.sharePerOwner > 0.005,
                firstNegative = report.sharedBalance > 0.005
            )
        }

        item { SectionHeader("Reference Status") }
        item { ReportPair("Unpaid Bills", report.unpaidBills, "Payroll Unpaid", report.unpaidPayroll) }

        item {
            SectionHeader(
                "Owner Balances as of ${formatDate(report.asOf)}",
                "Positive = amount still due. Negative = owner credit. Contributions reduce only that owner's balance."
            )
        }
        items(report.ownerBalances, key = { it.name }) { owner ->
            ReportOwnerRow(owner)
        }

        item { SectionHeader("Expense Record") }
        if (report.expenseRows.isEmpty()) {
            item { EmptyState("No expenses recorded for this report period.") }
        } else {
            items(report.expenseRows, key = { it.id }) { expense ->
                ReportExpenseRowV130(expense)
            }
        }
    }
}

@Composable
private fun ReportPair(
    firstLabel: String,
    firstValue: Double,
    secondLabel: String,
    secondValue: Double,
    firstNegative: Boolean = false,
    secondNegative: Boolean = false
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            firstLabel,
            money(firstValue),
            Modifier.weight(1f),
            if (firstNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        StatCard(
            secondLabel,
            money(secondValue),
            Modifier.weight(1f),
            if (secondNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ReportOwnerRow(owner: OwnerSummary) {
    val status = when {
        owner.outstandingBalance > 0.005 -> "DUE"
        owner.outstandingBalance < -0.005 -> "CREDIT"
        else -> "SETTLED"
    }
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(owner.name, fontWeight = FontWeight.SemiBold)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(money(abs(owner.outstandingBalance)), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReportExpenseRowV130(expense: ExpenseEntity) {
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(expense.description, fontWeight = FontWeight.SemiBold)
                Text(
                    "${expense.category} • ${formatDate(LocalDate.ofEpochDay(expense.incurredEpochDay))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(money(expense.amount), fontWeight = FontWeight.Bold)
        }
    }
}

private fun weeklyPeriods(today: LocalDate): List<ReportPeriod> {
    val result = mutableListOf<ReportPeriod>()
    var start = PixnetRules.START_DATE
    var number = 1
    val limit = minOf(today, PixnetRules.END_DATE)
    while (!start.isAfter(limit)) {
        val end = minOf(start.plusDays(6), PixnetRules.END_DATE)
        result += ReportPeriod("Week $number", start, end)
        start = end.plusDays(1)
        number++
    }
    return result.ifEmpty { listOf(ReportPeriod("Week 1", PixnetRules.START_DATE, PixnetRules.START_DATE.plusDays(6))) }
}

private fun makeSimpleReport(state: PixnetState, period: ReportPeriod, today: LocalDate): SimpleReport {
    val asOf = minOf(today, period.end, PixnetRules.END_DATE)
    val reportCollections = state.collections.filter {
        val date = LocalDate.ofEpochDay(it.dateEpochDay)
        date in period.start..asOf
    }
    val reportExpenses = state.expenses.filter {
        val date = LocalDate.ofEpochDay(it.incurredEpochDay)
        date in period.start..asOf
    }.sortedByDescending { it.incurredEpochDay }
    val reportAttendance = state.attendance.filter {
        val date = LocalDate.ofEpochDay(it.dateEpochDay)
        date in period.start..asOf
    }
    val reportContributions = state.ownerContributions.filter {
        val date = LocalDate.ofEpochDay(it.dateEpochDay)
        date in period.start..asOf
    }

    val asOfState = BusinessCalculator.calculate(
        collections = state.collections,
        expenses = state.expenses,
        attendance = state.attendance,
        payrollPayments = state.payrollPayments,
        ownerContributions = state.ownerContributions,
        today = asOf
    )

    return SimpleReport(
        period = period,
        asOf = asOf,
        isFinal = !today.isBefore(period.end),
        pisonet = reportCollections.sumOf { it.pisonet },
        printer = reportCollections.sumOf { it.printer },
        other = reportCollections.sumOf { it.otherIncome },
        expenses = reportExpenses.sumOf { it.amount },
        payroll = reportAttendance.sumOf { PixnetRules.staffSalary(it.staff) },
        ownerContributions = reportContributions.sumOf { it.amount },
        ownerBalances = asOfState.owners,
        unpaidBills = asOfState.unpaidBills,
        unpaidPayroll = asOfState.outstandingPayroll,
        expenseRows = reportExpenses
    )
}

private fun reportText(report: SimpleReport, mode: ReportMode): String = buildString {
    appendLine("PIXNET BUSINESS REPORT")
    appendLine(if (mode == ReportMode.WEEKLY) "WEEKLY" else "PER CUTOFF")
    appendLine("${report.period.label}: ${formatDate(report.period.start)} - ${formatDate(report.period.end)}")
    appendLine(if (report.isFinal) "Status: FINAL" else "Status: PROJECTED")
    appendLine("Cutoff is for reporting only; no balance is settled or reset.")
    appendLine()
    appendLine("COLLECTIONS")
    appendLine("Pisonet: ${money(report.pisonet)}")
    appendLine("Printer: ${money(report.printer)}")
    appendLine("Other: ${money(report.other)}")
    appendLine("Total Collection: ${money(report.collection)}")
    appendLine()
    appendLine("EXPENSES & PAYROLL")
    appendLine("Expenses: ${money(report.expenses)}")
    appendLine("Payroll: ${money(report.payroll)}")
    appendLine("Total Cost: ${money(report.totalCost)}")
    appendLine()
    appendLine("SHARED RESULT")
    appendLine(if (report.sharedBalance >= 0) "Shared Balance: ${money(report.sharedBalance)}" else "Business Surplus: ${money(abs(report.sharedBalance))}")
    appendLine(if (report.sharePerOwner >= 0) "Share / Owner: ${money(report.sharePerOwner)}" else "Credit / Owner: ${money(abs(report.sharePerOwner))}")
    appendLine("Owner Contributions Received: ${money(report.ownerContributions)}")
    appendLine()
    appendLine("OWNER BALANCES AS OF ${formatDate(report.asOf)}")
    report.ownerBalances.forEach { owner ->
        val status = when {
            owner.outstandingBalance > 0.005 -> "DUE"
            owner.outstandingBalance < -0.005 -> "CREDIT"
            else -> "SETTLED"
        }
        appendLine("${owner.name}: ${money(abs(owner.outstandingBalance))} $status")
    }
}
