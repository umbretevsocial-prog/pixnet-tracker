package com.pixnet.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixnet.tracker.data.OwnerContributionEntity
import com.pixnet.tracker.model.OwnerSummary
import com.pixnet.tracker.model.PixnetRules
import com.pixnet.tracker.model.PixnetState
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

@Composable
fun OwnersScreen(
    state: PixnetState,
    viewModel: PixnetViewModel,
    modifier: Modifier = Modifier
) {
    var showContribution by remember { mutableStateOf(false) }
    val ownersDue = state.owners.sumOf { max(0.0, it.outstandingBalance) }
    val credits = state.owners.sumOf { max(0.0, -it.outstandingBalance) }
    val projectedAdditionalPerOwner =
        (state.unpaidBills + state.outstandingPayroll) / PixnetRules.OWNERS.size

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                "Owner Balances",
                "Paid bills + paid payroll − collections are shared equally by 7. Unpaid items stay projected.",
                trailing = {
                    Button(onClick = { showContribution = true }) {
                        Text("+ Contribution")
                    }
                }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    if (state.sharePerOwner >= 0) "Operating Share / Owner" else "Operating Credit / Owner",
                    money(abs(state.sharePerOwner)),
                    Modifier.weight(1f),
                    if (state.sharePerOwner > 0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                StatCard(
                    "Owners Owe",
                    money(ownersDue),
                    Modifier.weight(1f),
                    if (ownersDue > 0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }

        item {
            StatCard(
                "Projected Additional / Owner",
                money(projectedAdditionalPerOwner),
                Modifier.fillMaxWidth()
            )
        }

        if (credits > 0.005) {
            item { StatCard("Owner Credits", money(credits), Modifier.fillMaxWidth()) }
        }

        item {
            Card {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("How this works", fontWeight = FontWeight.Bold)
                    Text("• All 7 owners are treated exactly the same.")
                    Text("• Only PAID bills are included in the actual owner share.")
                    Text("• Attendance creates payroll outstanding; payroll enters the share only when paid.")
                    Text("• Collections continuously reduce the actual shared operating balance.")
                    Text("• Unpaid bills/payroll are projected only and do not increase owner dues yet.")
                    Text("• Each owner's recorded contributions reduce only that owner's balance.")
                    Text("• Cutoffs only report the balance; they never settle or reset it.")
                }
            }
        }

        items(state.owners, key = { it.name }) { owner ->
            SimpleOwnerCard(owner)
        }

        item { SectionHeader("Recent Owner Contributions") }
        if (state.ownerContributions.isEmpty()) {
            item { EmptyState("No owner contributions recorded yet.") }
        } else {
            items(state.ownerContributions.take(30), key = { it.id }) { entry ->
                ContributionRowV130(entry, onDelete = { viewModel.deleteOwnerContribution(entry) })
            }
        }
    }

    if (showContribution) {
        AddContributionDialogV130(
            onDismiss = { showContribution = false },
            onSave = { date, owner, amount, reference ->
                viewModel.addOwnerContribution(date, owner, amount, reference)
                showContribution = false
            }
        )
    }
}

@Composable
private fun SimpleOwnerCard(owner: OwnerSummary) {
    val status = when {
        owner.outstandingBalance > 0.005 -> "DUE"
        owner.outstandingBalance < -0.005 -> "CREDIT"
        else -> "SETTLED"
    }

    Card {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text(owner.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusPill(status)
            }
            OwnerMetricV130("Opening balance", owner.openingBalance)
            OwnerMetricV130("Equal operating share", owner.newContributionsDue)
            OwnerMetricV130("Contributions paid", owner.cashContributions)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth()) {
                Text(if (owner.outstandingBalance < -0.005) "Credit" else "Current balance", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(
                    money(abs(owner.outstandingBalance)),
                    fontWeight = FontWeight.Bold,
                    color = if (owner.outstandingBalance > 0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun OwnerMetricV130(label: String, value: Double) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(money(value))
    }
}

@Composable
private fun ContributionRowV130(entry: OwnerContributionEntity, onDelete: () -> Unit) {
    val date = LocalDate.ofEpochDay(entry.dateEpochDay)
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(entry.ownerName, fontWeight = FontWeight.Bold)
                    Text(formatDate(date), style = MaterialTheme.typography.bodySmall)
                }
                Text(money(entry.amount), fontWeight = FontWeight.Bold)
            }
            if (entry.reference.isNotBlank()) {
                Text(entry.reference, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun AddContributionDialogV130(
    onDismiss: () -> Unit,
    onSave: (LocalDate, String, Double, String) -> Unit
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var owner by remember { mutableStateOf(PixnetRules.OWNERS.first()) }
    var amountText by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var ownerMenu by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Owner Contribution") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateButton("Payment date", date, { date = it })
                Box {
                    OutlinedButton(onClick = { ownerMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(owner) }
                    DropdownMenu(expanded = ownerMenu, onDismissRequest = { ownerMenu = false }) {
                        PixnetRules.OWNERS.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { owner = name; ownerMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    prefix = { Text("₱") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Reference / Notes") }
                )
                Text(
                    "This payment reduces only this owner's running balance. It is not business income.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(enabled = amount > 0, onClick = { onSave(date, owner, amount, reference) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
