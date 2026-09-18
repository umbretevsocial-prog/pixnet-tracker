package com.pixnet.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixnet.tracker.model.DailyBalance
import com.pixnet.tracker.model.PixnetRules
import com.pixnet.tracker.model.PixnetState
import kotlin.math.abs

@Composable
fun RunningBalanceScreen(state: PixnetState, modifier: Modifier = Modifier) {
    val latest = state.dailyBalances.lastOrNull()
    val shared = latest?.pixnetCashBalance ?: 0.0
    val perOwner = shared / PixnetRules.OWNERS.size

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                "Live Actual Shared Balance",
                latest?.let { "Paid bills/payroll only through ${formatDate(it.date)} • no cutoff settlement" }
                    ?: "Starts when the tracking period begins."
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    if (shared >= 0) "Shared Balance" else "Business Surplus",
                    money(abs(shared)),
                    Modifier.weight(1f),
                    if (shared > 0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                StatCard(
                    if (perOwner >= 0) "Share / Owner" else "Credit / Owner",
                    money(abs(perOwner)),
                    Modifier.weight(1f),
                    if (perOwner > 0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Projected Bills", money(state.unpaidBills), Modifier.weight(1f))
                StatCard("Projected Payroll", money(state.outstandingPayroll), Modifier.weight(1f))
            }
        }

        item {
            Text("Daily actual movement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (state.dailyBalances.isEmpty()) {
            item { EmptyState("No running-balance dates yet.") }
        } else {
            items(state.dailyBalances.asReversed(), key = { it.date.toEpochDay() }) { day ->
                SharedBalanceRow(day)
            }
        }
    }
}

@Composable
private fun SharedBalanceRow(day: DailyBalance) {
    val hasActivity = day.income != 0.0 || day.expensesIncurred != 0.0 || day.payrollIncurred != 0.0 || day.ownerContributionsIn != 0.0

    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(formatDate(day.date), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (day.pixnetCashBalance >= 0) money(day.pixnetCashBalance) else "Surplus ${money(abs(day.pixnetCashBalance))}",
                    fontWeight = FontWeight.Bold
                )
            }

            if (hasActivity) {
                if (day.income != 0.0) SharedLine("Collections", -day.income)
                if (day.expensesIncurred != 0.0) SharedLine("Paid Expenses", day.expensesIncurred)
                if (day.payrollIncurred != 0.0) SharedLine("Paid Payroll", day.payrollIncurred)
                if (day.ownerContributionsIn != 0.0) {
                    Text(
                        "Owner contributions received: ${money(day.ownerContributionsIn)} (reduces individual owner balances only)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text("No movement", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth()) {
                Text("Running share / owner", Modifier.weight(1f))
                Text(money(day.pixnetCashBalance / PixnetRules.OWNERS.size))
            }
        }
    }
}

@Composable
private fun SharedLine(label: String, value: Double) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(
            if (value >= 0) "+${money(value)}" else money(value),
            color = if (value > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}
