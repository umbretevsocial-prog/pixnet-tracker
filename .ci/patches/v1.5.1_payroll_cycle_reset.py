from pathlib import Path

def replace_or_fail(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Could not find {label}")
    return text.replace(old, new, 1)

# BusinessModels: reset current payroll-cycle days/earned/paid after a full settlement.
path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/model/BusinessModels.kt")
text = path.read_text()

old = '''        val staffPayrollSummaries = PixnetRules.STAFF.map { staff ->
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
        }'''

new = '''        val staffPayrollSummaries = PixnetRules.STAFF.map { staff ->
            val attendanceRows = activeAttendance
                .filter { it.staff == staff }
                .sortedBy { it.dateEpochDay }
            val paymentRows = activePayrollPayments
                .filter { it.staff == staff }
                .sortedWith(compareBy<PayrollPaymentEntity> { it.dateEpochDay }.thenBy { it.id })

            val lifetimeEarned = attendanceRows.sumOf { PixnetRules.staffSalary(it.staff) }
            val lifetimePaid = paymentRows.sumOf { it.amount }
            val outstanding = max(0.0, lifetimeEarned - lifetimePaid)

            // Find the latest date where cumulative payroll became fully settled.
            // Attendance on or before that settlement date belongs to the completed cycle.
            val eventDates = (attendanceRows.map { it.dateEpochDay } + paymentRows.map { it.dateEpochDay })
                .distinct()
                .sorted()
            var cumulativeEarned = 0.0
            var cumulativePaid = 0.0
            var lastSettlementEpochDay: Long? = null
            eventDates.forEach { epochDay ->
                cumulativeEarned += attendanceRows
                    .filter { it.dateEpochDay == epochDay }
                    .sumOf { PixnetRules.staffSalary(it.staff) }
                cumulativePaid += paymentRows
                    .filter { it.dateEpochDay == epochDay }
                    .sumOf { it.amount }
                if (cumulativePaid > 0.005 && cumulativePaid + 0.005 >= cumulativeEarned) {
                    lastSettlementEpochDay = epochDay
                }
            }

            val currentAttendance = attendanceRows.filter { row ->
                lastSettlementEpochDay == null || row.dateEpochDay > lastSettlementEpochDay!!
            }
            val currentPayments = paymentRows.filter { row ->
                lastSettlementEpochDay == null || row.dateEpochDay > lastSettlementEpochDay!!
            }

            StaffPayrollSummary(
                name = staff,
                daysWorked = currentAttendance.size,
                salaryEarned = currentAttendance.sumOf { PixnetRules.staffSalary(it.staff) },
                salaryPaid = currentPayments.sumOf { it.amount },
                outstandingBalance = outstanding
            )
        }'''

text = replace_or_fail(text, old, new, "staff payroll summary block")
path.write_text(text)

# Attendance screen: payment dialog also uses the current payroll cycle.
path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/ui/AttendanceScreen.kt")
text = path.read_text()

old = '''private fun payrollAsOf(
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
}'''

new = '''private fun payrollAsOf(
    state: PixnetState,
    staff: String,
    date: LocalDate
): PayrollAsOf {
    val attendanceRows = state.attendance
        .filter {
            it.staff == staff && !LocalDate.ofEpochDay(it.dateEpochDay).isAfter(date)
        }
        .sortedBy { it.dateEpochDay }
    val paymentRows = state.payrollPayments
        .filter {
            it.staff == staff && !LocalDate.ofEpochDay(it.dateEpochDay).isAfter(date)
        }
        .sortedWith(compareBy<PayrollPaymentEntity> { it.dateEpochDay }.thenBy { it.id })

    val lifetimeEarned = attendanceRows.sumOf { PixnetRules.staffSalary(it.staff) }
    val lifetimePaid = paymentRows.sumOf { it.amount }
    val balance = max(0.0, lifetimeEarned - lifetimePaid)

    val eventDates = (attendanceRows.map { it.dateEpochDay } + paymentRows.map { it.dateEpochDay })
        .distinct()
        .sorted()
    var cumulativeEarned = 0.0
    var cumulativePaid = 0.0
    var lastSettlementEpochDay: Long? = null
    eventDates.forEach { epochDay ->
        cumulativeEarned += attendanceRows
            .filter { it.dateEpochDay == epochDay }
            .sumOf { PixnetRules.staffSalary(it.staff) }
        cumulativePaid += paymentRows
            .filter { it.dateEpochDay == epochDay }
            .sumOf { it.amount }
        if (cumulativePaid > 0.005 && cumulativePaid + 0.005 >= cumulativeEarned) {
            lastSettlementEpochDay = epochDay
        }
    }

    val currentAttendance = attendanceRows.filter { row ->
        lastSettlementEpochDay == null || row.dateEpochDay > lastSettlementEpochDay!!
    }
    val currentPayments = paymentRows.filter { row ->
        lastSettlementEpochDay == null || row.dateEpochDay > lastSettlementEpochDay!!
    }

    return PayrollAsOf(
        earned = currentAttendance.sumOf { PixnetRules.staffSalary(it.staff) },
        paid = currentPayments.sumOf { it.amount },
        balance = balance,
        daysWorked = currentAttendance.size
    )
}'''

text = replace_or_fail(text, old, new, "payrollAsOf function")

text = text.replace(
    '"Each employee has their own Earned, Paid, and Balance. Payments cannot reduce another employee\'s balance."',
    '"Current work days reset to 0 after that staff member is fully paid. Partial payments do not reset the cycle."'
)

text = text.replace(
    '"A simple payroll receipt will be available right after saving."',
    '"A full payment settles the current payroll cycle and resets current work days to 0. Partial payment keeps the cycle open."'
)

path.write_text(text)
print("PIXNET v1.5.1 payroll cycle reset applied")
