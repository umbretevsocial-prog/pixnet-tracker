package com.pixnet.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixnet.tracker.data.ExpenseEntity
import com.pixnet.tracker.model.PixnetRules
import com.pixnet.tracker.model.PixnetState
import java.time.LocalDate

@Composable
fun ExpensesScreen(
    state: PixnetState,
    viewModel: PixnetViewModel,
    modifier: Modifier = Modifier
) {
    var showAdd by remember { mutableStateOf(false) }
    var expenseToPay by remember { mutableStateOf<ExpenseEntity?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                "Expenses",
                "Enter bills when incurred. Paid bills become Owner Advance.",
                trailing = {
                    Button(onClick = { showAdd = true }) {
                        Text("+ Expense")
                    }
                }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Unpaid Bills", money(state.unpaidBills), Modifier.weight(1f))
                StatCard("Owner Advance", money(state.currentOwnerAdvance), Modifier.weight(1f))
            }
        }

        if (state.expenses.isEmpty()) {
            item { EmptyState("No expenses recorded yet.") }
        } else {
            items(state.expenses, key = { it.id }) { entry ->
                ExpenseRow(
                    entry = entry,
                    onMarkPaid = { expenseToPay = entry },
                    onDelete = { viewModel.deleteExpense(entry) }
                )
            }
        }
    }

    if (showAdd) {
        AddExpenseDialog(
            onDismiss = { showAdd = false },
            onSave = { date, category, description, amount, paidDate, notes ->
                viewModel.addExpense(date, category, description, amount, paidDate, notes)
                showAdd = false
            }
        )
    }

    expenseToPay?.let { expense ->
        MarkPaidDialog(
            expense = expense,
            onDismiss = { expenseToPay = null },
            onSave = { paidDate ->
                viewModel.markExpensePaid(expense, paidDate)
                expenseToPay = null
            }
        )
    }
}

@Composable
private fun ExpenseRow(
    entry: ExpenseEntity,
    onMarkPaid: () -> Unit,
    onDelete: () -> Unit
) {
    val incurred = LocalDate.ofEpochDay(entry.incurredEpochDay)
    val paidDate = entry.paidEpochDay?.let(LocalDate::ofEpochDay)

    Card {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(entry.description, fontWeight = FontWeight.Bold)
                    Text(
                        "${entry.category} • ${formatDate(incurred)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(money(entry.amount), fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(if (paidDate == null) "UNPAID" else "PAID")
                if (paidDate != null) {
                    Text(
                        "Paid ${formatDate(paidDate)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (entry.notes.isNotBlank()) {
                Text(entry.notes, style = MaterialTheme.typography.bodySmall)
            }

            Row {
                if (paidDate == null) {
                    TextButton(onClick = onMarkPaid) {
                        Text("Mark Paid")
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onSave: (LocalDate, String, String, Double, LocalDate?, String) -> Unit
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    val expenseCategories = listOf(
        "Rent",
        "Electricity",
        "Internet",
        "Water",
        "Equipment / Computer Parts",
        "Repairs & Maintenance",
        "Printer Supplies",
        "Shop Supplies",
        "Permits & Fees",
        "Other"
    )
    var category by remember { mutableStateOf("") }
    var customCategory by remember { mutableStateOf("") }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var paidNow by remember { mutableStateOf(false) }
    var paidDate by remember { mutableStateOf(LocalDate.now()) }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val savedCategory = when {
        category == "Other" -> customCategory.trim()
        else -> category
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                DateButton("Incurred", date, { date = it })
                ExposedDropdownMenuBox(
                    expanded = categoryMenuOpen,
                    onExpandedChange = { categoryMenuOpen = !categoryMenuOpen }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        placeholder = { Text("Select category") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuOpen)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuOpen,
                        onDismissRequest = { categoryMenuOpen = false }
                    ) {
                        expenseCategories.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    category = option
                                    if (option != "Other") customCategory = ""
                                    categoryMenuOpen = false
                                }
                            )
                        }
                    }
                }
                if (category == "Other") {
                    OutlinedTextField(
                        value = customCategory,
                        onValueChange = { customCategory = it },
                        label = { Text("Other category") },
                        placeholder = { Text("Type category") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    prefix = { Text("₱") },
                    singleLine = true
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = paidNow, onCheckedChange = { paidNow = it })
                    Text("Paid by Von already")
                }
                if (paidNow) {
                    DateButton("Paid", paidDate, { paidDate = it })
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") }
                )
                Text(
                    if (paidNow) {
                        "This will add ${money(amount)} to Owner Advance."
                    } else {
                        "This will remain an unpaid PIXNET bill."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = description.isNotBlank() && savedCategory.isNotBlank() && amount > 0,
                onClick = {
                    onSave(
                        date,
                        savedCategory,
                        description,
                        amount,
                        if (paidNow) paidDate else null,
                        notes
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MarkPaidDialog(
    expense: ExpenseEntity,
    onDismiss: () -> Unit,
    onSave: (LocalDate) -> Unit
) {
    var paidDate by remember { mutableStateOf(LocalDate.now()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark Expense Paid") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(expense.description, fontWeight = FontWeight.Bold)
                Text(money(expense.amount))
                DateButton("Paid date", paidDate, { paidDate = it })
                Text(
                    "This amount will be added to Owner Advance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(paidDate) }) { Text("Mark Paid") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
