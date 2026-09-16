package com.pixnet.tracker.model

import com.pixnet.tracker.data.*
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

data class CutoffPeriod(
    val name: String,
    val start: LocalDate,
    val end: LocalDate
)

data class PeriodSummary(
    val period: CutoffPeriod,
    val isFinal: Boolean,
    val income: Double,
    val nonPayrollExpenses: Double,
    val salaryExpense: Double,
    val totalExpenses: Double,
    val netEarnings: Double,
    val openingOwnerAdvance: Double,
    val ownerAdvanceAdded: Double,
    val ownerContributionsReceived: Double,
    val fundsAvailable: Double,
    val ownerAdvanceRepaid: Double,
    val endingOwnerAdvance: Double,
    val ownerContributionNeeded: Double,
    val contributionPerOwner: Double,
    val profitPool: Double,
    val grossProfitSharePerOwner: Double,
    val profitOffsetTotal: Double,
    val cashProfitPayoutTotal: Double,
    val cashCarryForward: Double,
    val unpaidBillsReserve: Double,
    val unpaidPayrollReserve: Double
)

data class StaffPayrollSummary(
    val name: String,
    val daysWorked: Int,
    val salaryEarned: Double,
    val salaryPaid: Double,
    val outstandingBalance: Double
)

data class OwnerSummary(
    val name: String,
    val openingBalance: Double,
    val newContributionsDue: Double,
    val cashContributions: Double,
    val profitShareOffset: Double,
    val cashProfitPaid: Double,
    val outstandingBalance: Double
)

data class DailyBalance(
    val date: LocalDate,
    val income: Double,
    val expensesIncurred: Double,
    val payrollIncurred: Double,
    val netEarningsMovement: Double,
    val runningNetEarnings: Double,
    val ownerContributionsIn: Double,
    val ownerAdvanceRepaid: Double,
    val cashProfitShareOut: Double,
    val pixnetCashMovement: Double,
    val pixnetCashBalance: Double
)

data class PixnetState(
    val collections: List<CollectionEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val attendance: List<AttendanceEntity> = emptyList(),
    val payrollPayments: List<PayrollPaymentEntity> = emptyList(),
    val ownerContributions: List<OwnerContributionEntity> = emptyList(),
    val periods: List<PeriodSummary> = emptyList(),
    val owners: List<OwnerSummary> = emptyList(),
    val staffPayroll: List<StaffPayrollSummary> = emptyList(),
    val dailyBalances: List<DailyBalance> = emptyList(),
    val currentPeriod: PeriodSummary? = null,
    val currentOwnerAdvance: Double = 0.0,
    val currentPixnetCash: Double = 0.0,
    val outstandingPayroll: Double = 0.0,
    val unpaidBills: Double = 0.0,
    val totalCollections: Double = 0.0,
    val totalOperatingExpenses: Double = 0.0,
    val totalPayrollExpense: Double = 0.0,
    val sharedBalance: Double = 0.0,
    val sharePerOwner: Double = 0.0
)

object PixnetRules {
    val START_DATE: LocalDate = LocalDate.of(2026, 8, 17)
    val END_DATE: LocalDate = LocalDate.of(2027, 1, 17)

    const val OPENING_CASH = 0.0
    // Retained only for historical reference. v1.3+ does not use Owner Advance accounting.
    const val OPENING_OWNER_ADVANCE = 4580.0
    const val STAFF_RATE = 375.0
    const val PRIMARY_PAYER = "Von Umbrete"

    val STAFF = listOf("Mayanne", "Hannah")

    val OWNERS = listOf(
        "Gia Suarez",
        "Ian Escalona",
        "Kevin Lorica",
        "Rendell Arpon",
        "Vanessa Muje",
        "Vince Tapire",
        "Von Umbrete"
    )

    // Existing unresolved balances carried into the Aug 17 fresh start.
    val OPENING_OWNER_BALANCES = mapOf(
        "Gia Suarez" to 0.0,
        "Ian Escalona" to 3128.0,
        "Kevin Lorica" to 0.0,
        "Rendell Arpon" to 0.0,
        "Vanessa Muje" to 2062.0,
        "Vince Tapire" to 0.0,
        "Von Umbrete" to 0.0
    )

    // Cutoffs are REPORTING WINDOWS ONLY. They never settle or reset money.
    val PERIODS = listOf(
        CutoffPeriod("August–September", LocalDate.of(2026, 8, 17), LocalDate.of(2026, 9, 13)),
        CutoffPeriod("September–October", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 18)),
        CutoffPeriod("October–November", LocalDate.of(2026, 10, 19), LocalDate.of(2026, 11, 15)),
        CutoffPeriod("November–December", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 13)),
        CutoffPeriod("December–January", LocalDate.of(2026, 12, 14), LocalDate.of(2027, 1, 17))
    )

    fun periodFor(date: LocalDate): CutoffPeriod =
        PERIODS.firstOrNull { !date.isBefore(it.start) && !date.isAfter(it.end) }
            ?: if (date.isBefore(START_DATE)) PERIODS.first() else PERIODS.last()

    fun staffSalary(staff: String): Double =
        if (staff in STAFF) STAFF_RATE else 0.0
}

object BusinessCalculator {
    fun calculate(
        collections: List<CollectionEntity>,
        expenses: List<ExpenseEntity>,
        attendance: List<AttendanceEntity>,
        payrollPayments: List<PayrollPaymentEntity>,
        ownerContributions: List<OwnerContributionEntity>,
        today: LocalDate = LocalDate.now()
    ): PixnetState {
        val effectiveToday = minOf(today, PixnetRules.END_DATE)

        fun onOrBefore(epochDay: Long): Boolean =
            !LocalDate.ofEpochDay(epochDay).isAfter(effectiveToday)

        val activeCollections = collections.filter { onOrBefore(it.dateEpochDay) }
        val activeExpenses = expenses.filter { onOrBefore(it.incurredEpochDay) }
        val activeAttendance = attendance.filter { onOrBefore(it.dateEpochDay) }
        val activePayrollPayments = payrollPayments.filter { onOrBefore(it.dateEpochDay) }
        val activeOwnerContributions = ownerContributions.filter { onOrBefore(it.dateEpochDay) }

        val totalCollections = activeCollections.sumOf { it.pisonet + it.printer + it.otherIncome }
        val totalOperatingExpenses = activeExpenses.sumOf { it.amount }
        val totalPayrollExpense = activeAttendance.sumOf { PixnetRules.staffSalary(it.staff) }

        // Positive = owners need to shoulder money. Negative = business surplus/credit.
        val sharedBalance = totalOperatingExpenses + totalPayrollExpense - totalCollections
        val sharePerOwner = sharedBalance / PixnetRules.OWNERS.size

        val contributionsByOwner = PixnetRules.OWNERS.associateWith { owner ->
            activeOwnerContributions.filter { it.ownerName == owner }.sumOf { it.amount }
        }

        val ownerSummaries = PixnetRules.OWNERS.map { owner ->
            val opening = PixnetRules.OPENING_OWNER_BALANCES[owner] ?: 0.0
            val cashContribution = contributionsByOwner[owner] ?: 0.0

            // Von is the operating payer. When the equal operating share is positive,
            // his own 1/7 is already covered by the cash he fronted for PIXNET.
            // A negative equal share remains as a credit/profit position like every owner.
            val autoCoveredByVon = if (owner == PixnetRules.PRIMARY_PAYER) {
                max(0.0, sharePerOwner)
            } else 0.0

            val balance = opening + sharePerOwner - cashContribution - autoCoveredByVon

            OwnerSummary(
                name = owner,
                openingBalance = opening,
                newContributionsDue = sharePerOwner,
                cashContributions = cashContribution,
                profitShareOffset = autoCoveredByVon,
                cashProfitPaid = 0.0,
                outstandingBalance = balance
            )
        }

        val periodSummaries = PixnetRules.PERIODS.map { period ->
            val periodEnd = minOf(period.end, effectiveToday)
            val hasStarted = !effectiveToday.isBefore(period.start)

            val periodCollections = if (hasStarted) activeCollections.filter {
                LocalDate.ofEpochDay(it.dateEpochDay) in period.start..periodEnd
            } else emptyList()
            val income = periodCollections.sumOf { it.pisonet + it.printer + it.otherIncome }

            val periodExpenses = if (hasStarted) activeExpenses.filter {
                LocalDate.ofEpochDay(it.incurredEpochDay) in period.start..periodEnd
            } else emptyList()
            val nonPayrollExpenses = periodExpenses.sumOf { it.amount }

            val periodAttendance = if (hasStarted) activeAttendance.filter {
                LocalDate.ofEpochDay(it.dateEpochDay) in period.start..periodEnd
            } else emptyList()
            val salaryExpense = periodAttendance.sumOf { PixnetRules.staffSalary(it.staff) }
            val totalExpenses = nonPayrollExpenses + salaryExpense
            val netEarnings = income - totalExpenses
            val periodSharedBalance = totalExpenses - income
            val periodSharePerOwner = periodSharedBalance / PixnetRules.OWNERS.size

            val contributionsInPeriod = if (hasStarted) activeOwnerContributions.filter {
                LocalDate.ofEpochDay(it.dateEpochDay) in period.start..periodEnd
            }.sumOf { it.amount } else 0.0

            val reportDate = if (!hasStarted) period.start.minusDays(1) else periodEnd
            val unpaidBillsReserve = activeExpenses.filter {
                val incurred = LocalDate.ofEpochDay(it.incurredEpochDay)
                val paid = it.paidEpochDay?.let(LocalDate::ofEpochDay)
                !incurred.isAfter(reportDate) && (paid == null || paid.isAfter(reportDate))
            }.sumOf { it.amount }

            val unpaidPayrollReserve = PixnetRules.STAFF.sumOf { staff ->
                val earned = activeAttendance.filter {
                    it.staff == staff && !LocalDate.ofEpochDay(it.dateEpochDay).isAfter(reportDate)
                }.sumOf { PixnetRules.staffSalary(it.staff) }
                val paid = activePayrollPayments.filter {
                    it.staff == staff && !LocalDate.ofEpochDay(it.dateEpochDay).isAfter(reportDate)
                }.sumOf { it.amount }
                max(0.0, earned - paid)
            }

            PeriodSummary(
                period = period,
                isFinal = !today.isBefore(period.end),
                income = income,
                nonPayrollExpenses = nonPayrollExpenses,
                salaryExpense = salaryExpense,
                totalExpenses = totalExpenses,
                netEarnings = netEarnings,
                openingOwnerAdvance = 0.0,
                ownerAdvanceAdded = 0.0,
                ownerContributionsReceived = contributionsInPeriod,
                fundsAvailable = income + contributionsInPeriod,
                ownerAdvanceRepaid = 0.0,
                endingOwnerAdvance = 0.0,
                ownerContributionNeeded = max(0.0, periodSharedBalance),
                contributionPerOwner = periodSharePerOwner,
                profitPool = max(0.0, -periodSharedBalance),
                grossProfitSharePerOwner = max(0.0, -periodSharePerOwner),
                profitOffsetTotal = 0.0,
                cashProfitPayoutTotal = 0.0,
                cashCarryForward = 0.0,
                unpaidBillsReserve = unpaidBillsReserve,
                unpaidPayrollReserve = unpaidPayrollReserve
            )
        }

        val daily = mutableListOf<DailyBalance>()
        var runningNetEarnings = 0.0
        var runningSharedBalance = 0.0

        if (!effectiveToday.isBefore(PixnetRules.START_DATE)) {
            var date = PixnetRules.START_DATE
            while (!date.isAfter(effectiveToday)) {
                val income = activeCollections.filter {
                    LocalDate.ofEpochDay(it.dateEpochDay) == date
                }.sumOf { it.pisonet + it.printer + it.otherIncome }

                val expensesIncurred = activeExpenses.filter {
                    LocalDate.ofEpochDay(it.incurredEpochDay) == date
                }.sumOf { it.amount }

                val payrollIncurred = activeAttendance.filter {
                    LocalDate.ofEpochDay(it.dateEpochDay) == date
                }.sumOf { PixnetRules.staffSalary(it.staff) }

                val netMovement = income - expensesIncurred - payrollIncurred
                val sharedMovement = expensesIncurred + payrollIncurred - income
                runningNetEarnings += netMovement
                runningSharedBalance += sharedMovement

                val ownerContribIn = activeOwnerContributions.filter {
                    LocalDate.ofEpochDay(it.dateEpochDay) == date
                }.sumOf { it.amount }

                daily += DailyBalance(
                    date = date,
                    income = income,
                    expensesIncurred = expensesIncurred,
                    payrollIncurred = payrollIncurred,
                    netEarningsMovement = netMovement,
                    runningNetEarnings = runningNetEarnings,
                    ownerContributionsIn = ownerContribIn,
                    ownerAdvanceRepaid = 0.0,
                    cashProfitShareOut = 0.0,
                    pixnetCashMovement = sharedMovement,
                    pixnetCashBalance = runningSharedBalance
                )
                date = date.plusDays(1)
            }
        }

        val currentPeriod = periodSummaries.firstOrNull {
            effectiveToday in it.period.start..it.period.end
        } ?: periodSummaries.lastOrNull()

        val unpaidBillsNow = activeExpenses.filter {
            val incurred = LocalDate.ofEpochDay(it.incurredEpochDay)
            val paid = it.paidEpochDay?.let(LocalDate::ofEpochDay)
            !incurred.isAfter(effectiveToday) && (paid == null || paid.isAfter(effectiveToday))
        }.sumOf { it.amount }

        val staffPayrollSummaries = PixnetRules.STAFF.map { staff ->
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
        }
        val outstandingPayrollNow = staffPayrollSummaries.sumOf { it.outstandingBalance }

        return PixnetState(
            collections = collections,
            expenses = expenses,
            attendance = attendance,
            payrollPayments = payrollPayments,
            ownerContributions = ownerContributions,
            periods = periodSummaries,
            owners = ownerSummaries,
            staffPayroll = staffPayrollSummaries,
            dailyBalances = daily,
            currentPeriod = currentPeriod,
            currentOwnerAdvance = 0.0,
            currentPixnetCash = 0.0,
            outstandingPayroll = outstandingPayrollNow,
            unpaidBills = unpaidBillsNow,
            totalCollections = totalCollections,
            totalOperatingExpenses = totalOperatingExpenses,
            totalPayrollExpense = totalPayrollExpense,
            sharedBalance = sharedBalance,
            sharePerOwner = sharePerOwner
        )
    }
}
