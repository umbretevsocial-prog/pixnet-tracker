from pathlib import Path

def replace_or_fail(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Could not find {label}")
    return text.replace(old, new, 1)

# Business model: only PAID bills and PAID payroll are actual shared expenses.
path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/model/BusinessModels.kt")
text = path.read_text()

text = replace_or_fail(
    text,
    '''        val totalCollections = activeCollections.sumOf { it.pisonet + it.printer + it.otherIncome }
        val totalOperatingExpenses = activeExpenses.sumOf { it.amount }
        val totalPayrollExpense = activeAttendance.sumOf { PixnetRules.staffSalary(it.staff) }

        // Positive = owners need to shoulder money. Negative = business surplus/credit.
        val sharedBalance = totalOperatingExpenses + totalPayrollExpense - totalCollections''',
    '''        val totalCollections = activeCollections.sumOf { it.pisonet + it.printer + it.otherIncome }

        // ACTUAL shared expenses only count money that has already been paid.
        // Unpaid bills and earned-but-unpaid payroll remain projected/outstanding only.
        val totalOperatingExpenses = activeExpenses.filter { expense ->
            val paid = expense.paidEpochDay?.let(LocalDate::ofEpochDay)
            paid != null && !paid.isAfter(effectiveToday)
        }.sumOf { it.amount }
        val totalPayrollExpense = activePayrollPayments.sumOf { it.amount }

        // Positive = owners need to shoulder money. Negative = business surplus/credit.
        val sharedBalance = totalOperatingExpenses + totalPayrollExpense - totalCollections''',
    "actual totals block",
)

text = replace_or_fail(
    text,
    '''            val periodExpenses = if (hasStarted) activeExpenses.filter {
                LocalDate.ofEpochDay(it.incurredEpochDay) in period.start..periodEnd
            } else emptyList()
            val nonPayrollExpenses = periodExpenses.sumOf { it.amount }

            val periodAttendance = if (hasStarted) activeAttendance.filter {
                LocalDate.ofEpochDay(it.dateEpochDay) in period.start..periodEnd
            } else emptyList()
            val salaryExpense = periodAttendance.sumOf { PixnetRules.staffSalary(it.staff) }''',
    '''            val periodExpenses = if (hasStarted) activeExpenses.filter { expense ->
                val paid = expense.paidEpochDay?.let(LocalDate::ofEpochDay)
                paid != null && paid in period.start..periodEnd
            } else emptyList()
            val nonPayrollExpenses = periodExpenses.sumOf { it.amount }

            val periodPayrollPayments = if (hasStarted) activePayrollPayments.filter {
                LocalDate.ofEpochDay(it.dateEpochDay) in period.start..periodEnd
            } else emptyList()
            val salaryExpense = periodPayrollPayments.sumOf { it.amount }''',
    "period paid expense/payroll block",
)

text = replace_or_fail(
    text,
    '''                val expensesIncurred = activeExpenses.filter {
                    LocalDate.ofEpochDay(it.incurredEpochDay) == date
                }.sumOf { it.amount }

                val payrollIncurred = activeAttendance.filter {
                    LocalDate.ofEpochDay(it.dateEpochDay) == date
                }.sumOf { PixnetRules.staffSalary(it.staff) }''',
    '''                val expensesIncurred = activeExpenses.filter { expense ->
                    expense.paidEpochDay?.let(LocalDate::ofEpochDay) == date
                }.sumOf { it.amount }

                val payrollIncurred = activePayrollPayments.filter {
                    LocalDate.ofEpochDay(it.dateEpochDay) == date
                }.sumOf { it.amount }''',
    "daily paid expense/payroll block",
)

path.write_text(text)

# Expenses screen wording.
path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/ui/ExpensesScreen.kt")
text = path.read_text()
text = text.replace(
    '"Every expense is shared when incurred. PAID/UNPAID is only your payment reference.",',
    '"Unpaid bills stay PROJECTED only. A bill enters the shared expense only when marked PAID.",'
)
text = text.replace(
    '"Paid by Von already"',
    '"Paid already"'
)
path.write_text(text)

# Attendance screen wording.
path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/ui/AttendanceScreen.kt")
text = path.read_text()
text = text.replace(
    '"Attendance creates shared payroll expense. Salary payment status is only your payment reference.",',
    '"Attendance creates payroll outstanding only. It becomes a shared expense only when the staff is paid.",'
)
text = text.replace(
    '"Payments are attached to the selected employee for reference only. They do not create another expense. A payroll receipt can also be shared."',
    '"A salary becomes an actual shared expense only when this payment is recorded. Unpaid salary remains outstanding/projected."'
)
path.write_text(text)

print("PIXNET v1.5 paid-only actual accounting applied")
