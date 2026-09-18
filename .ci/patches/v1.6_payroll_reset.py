from pathlib import Path

def replace_or_fail(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Could not find {label}")
    return text.replace(old, new, 1)

# Payroll summary: show only currently unpaid work days.
path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/model/BusinessModels.kt")
text = path.read_text()

old = """        val staffPayrollSummaries = PixnetRules.STAFF.map { staff ->
            val attendanceRows = activeAttendance.filter { it.staff == staff }
            val salaryEarned = attendanceRows.sumOf { PixnetRules.staffSalary(it.staff) }
            val salaryPaid = activePayrollPayments.filter { it.staff == staff }.sumOf { it.amount }
            StaffPayrollSummary(
                name = staff,
                daysWorked = attendanceRows.size,
                salaryEarned = salaryEarned,
                salaryPaid = salaryPaid,
                outstandingBalance = max(0.0, salaryEarned - salaryPaid)
            )
        }"""
new = """        val staffPayrollSummaries = PixnetRules.STAFF.map { staff ->
            val attendanceRows = activeAttendance.filter { it.staff == staff }
            val salaryEarned = attendanceRows.sumOf { PixnetRules.staffSalary(it.staff) }
            val salaryPaid = activePayrollPayments.filter { it.staff == staff }.sumOf { it.amount }
            val fullyPaidDays = (salaryPaid / PixnetRules.STAFF_RATE).toInt()
            val unpaidDays = max(0, attendanceRows.size - fullyPaidDays)
            StaffPayrollSummary(
                name = staff,
                daysWorked = unpaidDays,
                salaryEarned = salaryEarned,
                salaryPaid = salaryPaid,
                outstandingBalance = max(0.0, salaryEarned - salaryPaid)
            )
        }"""
text = replace_or_fail(text, old, new, "staff payroll summary block")
path.write_text(text)

# Attendance UI: unpaid day counter resets after full settlement.
path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/ui/AttendanceScreen.kt")
text = path.read_text()

text = text.replace(
    '"${summary.daysWorked} day${if (summary.daysWorked == 1) "" else "s"} worked"',
    '"${summary.daysWorked} unpaid work day${if (summary.daysWorked == 1) "" else "s"}"'
)

old = """    return PayrollAsOf(
        earned = earned,
        paid = paid,
        balance = max(0.0, earned - paid),
        daysWorked = attendanceRows.size
    )"""
new = """    val fullyPaidDays = (paid / PixnetRules.STAFF_RATE).toInt()
    val unpaidDays = max(0, attendanceRows.size - fullyPaidDays)
    return PayrollAsOf(
        earned = earned,
        paid = paid,
        balance = max(0.0, earned - paid),
        daysWorked = unpaidDays
    )"""
text = replace_or_fail(text, old, new, "payrollAsOf return block")

text = text.replace(
    'Text("Days worked: ${payroll.daysWorked}")',
    'Text("Unpaid work days: ${payroll.daysWorked}")'
)

text = text.replace(
    '"Each employee has their own Earned, Paid, and Balance. Payments cannot reduce another employee\'s balance."',
    '"Work-day count shows only unpaid days. After full payment, it resets to 0 while attendance history stays saved."'
)

path.write_text(text)

# Remove obsolete Owner Advance wording from expense payment dialogs.
path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/ui/ExpensesScreen.kt")
text = path.read_text()
text = text.replace(
    '"This will add ${money(amount)} to Owner Advance."',
    '"This paid bill will be included in the actual shared expenses."'
)
text = text.replace(
    '"This amount will be added to Owner Advance."',
    '"Once marked paid, this bill will be included in the actual shared expenses."'
)
path.write_text(text)

print("PIXNET v1.6 payroll reset and payment wording applied")
