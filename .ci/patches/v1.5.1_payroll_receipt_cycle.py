from pathlib import Path

path = Path("pixnet-tracker-app/app/src/main/java/com/pixnet/tracker/ui/AttendanceScreen.kt")
text = path.read_text()

old = '''    val date = LocalDate.ofEpochDay(entry.dateEpochDay)
    val (periodStart, periodEnd) = payrollPeriodBounds(date)
    val attendanceDates = state.attendance
        .filter {
            if (it.staff != entry.staff) return@filter false
            val attendanceDate = LocalDate.ofEpochDay(it.dateEpochDay)
            !attendanceDate.isBefore(periodStart) &&
                !attendanceDate.isAfter(minOf(periodEnd, date))
        }
        .map { LocalDate.ofEpochDay(it.dateEpochDay) }
        .sorted()'''

new = '''    val date = LocalDate.ofEpochDay(entry.dateEpochDay)

    // Receipt follows the actual payroll cycle: attendance after the previous
    // full settlement up to this payment date, regardless of cutoff.
    val priorAttendance = state.attendance
        .filter {
            it.staff == entry.staff &&
                LocalDate.ofEpochDay(it.dateEpochDay).isBefore(date)
        }
        .sortedBy { it.dateEpochDay }
    val priorPayments = state.payrollPayments
        .filter {
            it.staff == entry.staff &&
                LocalDate.ofEpochDay(it.dateEpochDay).isBefore(date)
        }
        .sortedWith(compareBy<PayrollPaymentEntity> { it.dateEpochDay }.thenBy { it.id })

    val priorEventDates = (priorAttendance.map { it.dateEpochDay } + priorPayments.map { it.dateEpochDay })
        .distinct()
        .sorted()
    var cumulativeEarnedBefore = 0.0
    var cumulativePaidBefore = 0.0
    var previousSettlementEpochDay: Long? = null
    priorEventDates.forEach { epochDay ->
        cumulativeEarnedBefore += priorAttendance
            .filter { it.dateEpochDay == epochDay }
            .sumOf { PixnetRules.staffSalary(it.staff) }
        cumulativePaidBefore += priorPayments
            .filter { it.dateEpochDay == epochDay }
            .sumOf { it.amount }
        if (cumulativePaidBefore > 0.005 && cumulativePaidBefore + 0.005 >= cumulativeEarnedBefore) {
            previousSettlementEpochDay = epochDay
        }
    }

    val attendanceDates = state.attendance
        .filter {
            if (it.staff != entry.staff) return@filter false
            val attendanceDate = LocalDate.ofEpochDay(it.dateEpochDay)
            !attendanceDate.isAfter(date) &&
                (previousSettlementEpochDay == null || it.dateEpochDay > previousSettlementEpochDay!!)
        }
        .map { LocalDate.ofEpochDay(it.dateEpochDay) }
        .sorted()'''

if old not in text:
    raise SystemExit("Could not find payroll receipt attendance-period block")

path.write_text(text.replace(old, new, 1))
print("PIXNET v1.5.1 payroll receipt cycle dates applied")
