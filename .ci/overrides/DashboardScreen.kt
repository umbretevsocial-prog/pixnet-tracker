package com.pixnet.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixnet.tracker.model.PixnetRules
import com.pixnet.tracker.model.PixnetState
import kotlin.math.abs
import kotlin.math.max

@Composable
fun DashboardScreen(state: PixnetState, modifier: Modifier = Modifier) {
    val current = state.currentPeriod
    val totalDue = state.owners
        .filter { it.name != PixnetRules.PRIMARY_PAYER }
        .sumOf { max(0.0, it.outstandingBalance) }
    val totalCredits = state.owners.sumOf { max(0.0, -it.outstandingBalance) }
    val sharedPositive = state.sharedBalance >= 0.0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Business Dashboard",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Continuous owner accounting • cutoffs are reports only",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Collections", money(state.totalCollections), Modifier.weight(1f))
                StatCard(
                    "Expenses + Payroll",
                    money(state.totalOperatingExpenses + state.totalPayrollExpense),
                    Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    if (sharedPositive) "Shared Balance" else "Business Surplus",
                    money(abs(state.sharedBalance)),
                    Modifier.weight(1f),
                    if (sharedPositive && state.sharedBalance > 0.005) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                StatCard(
                    if (state.sharePerOwner >= 0) "Share / Owner" else "Credit / Owner",
                    money(abs(state.sharePerOwner)),
                    Modifier.weight(1f),
                    if (state.sharePerOwner > 0.005) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    "Owners Owe",
                    money(totalDue),
                    Modifier.weight(1f),
                    if (totalDue > 0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                StatCard("Owner Credits", money(totalCredits), Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Unpaid Bills", money(state.unpaidBills), Modifier.weight(1f))
                StatCard("Payroll Unpaid", money(state.outstandingPayroll), Modifier.weight(1f))
            }
        }

        item {
            SectionHeader(
                "Current Reporting Cutoff",
                "This is only a snapshot. Nothing is settled or reset when a cutoff ends."
            )
        }

        current?.let { period ->
            item {
                Card {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(period.period.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            StatusPill(statusText(period.isFinal))
                        }
                        DashboardLine("Collections", period.income)
                        DashboardLine("Expenses", period.nonPayrollExpenses)
                        DashboardLine("Payroll", period.salaryExpense)
                        DashboardLine("Net earnings", period.netEarnings, bold = true)
                        DashboardLine("Share / owner", period.contributionPerOwner, bold = true)
                    }
                }
            }
        }

        item {
            SectionHeader(
                "Owner Balances",
                "Positive = owner still owes. Negative = owner has credit. Von's own positive operating share is already covered by the cash he paid for PIXNET."
            )
        }

        items(state.owners, key = { it.name }) { owner ->
            val status = when {
                owner.outstandingBalance > 0.005 -> "DUE"
                owner.outstandingBalance < -0.005 -> "CREDIT"
                else -> "SETTLED"
            }
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(owner.name, fontWeight = FontWeight.Bold)
                        if (owner.cashContributions > 0.005) {
                            Text(
                                "Contributed ${money(owner.cashContributions)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (owner.outstandingBalance < -0.005) {
                                "Credit ${money(abs(owner.outstandingBalance))}"
                            } else {
                                money(max(0.0, owner.outstandingBalance))
                            },
                            fontWeight = FontWeight.Bold
                        )
                        StatusPill(status)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardLine(label: String, value: Double, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(money(value), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
