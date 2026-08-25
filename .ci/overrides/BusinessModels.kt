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
    val currentOwnerAdvance: Double = PixnetRules.OPENING_OWNER_ADVANCE,
    val currentPixnetCash: Double = PixnetRules.OPENING_CASH,
    val outstandingPayroll: Double = 0.0,
    val unpaidBills: Double = 0.0
)

object PixnetRules {
    val START_DATE: LocalDate = LocalDate.of(2026, 8, 17)
    val END_DATE: LocalDate = LocalDate.of(2027, 1, 17)

    const val OPENING_CASH = 0.0
    const val OPENING_OWNER_ADVANCE = 4580.0
    const val STAFF_RATE = 375.0

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

    val OPENING_OWNER_BALANCES = mapOf(
        "Gia Suarez" to 0.0,
        "Ian Escalona" to 3128.0,
        "Kevin Lorica" to 0.0,
        "Rendell Arpon" to 0.0,
        "Vanessa Muje" to 2062.0,
        "Vince Tapire" to 0.0,
        "Von Umbrete" to 0.0
    )

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
        if (staff == "Mayanne" || staff == "Hannah") STAFF_RATE else 0.0
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

        val ownerBalances = PixnetRules.OPENING_OWNER_BALANCES.toMutableMap()
        val ownerNewDue = PixnetRules.OWNERS.associateWith { 0.0 }.toMutableMap()
        val ownerCashContrib = PixnetRules.OWNERS.associateWith { 0.0 }.toMutableMap()
        val ownerOffsets = PixnetRules.OWNERS.associateWith { 0.0 }.toMutableMap()
        val ownerCashProfit = PixnetRules.OWNERS.associateWith { 0.0 }.toMutableMap()

        var ownerAdvance = PixnetRules.OPENING_OWNER_ADVANCE
        var cashCarry = PixnetRules.OPENING_CASH
        val periodSummaries = mutableListOf<PeriodSummary>()

        for (period in PixnetRules.PERIODS) {
            val isFinal = !today.isBefore(period.end)

            val periodCollections = collections.filter {
                LocalDate.ofEpochDay(it.dateEpochDay) in period.start..period.end
            }
            val income = periodCollections.sumOf { it.pisonet + it.printer + it.otherIncome }

            val periodExpenses = expenses.filter {
                LocalDate.ofEpochDay(it.incurredEpochDay) in period.start..period.end
            }
            val nonPayrollExpenses = periodExpenses.sumOf { it.amount }

            val periodAttendance = attendance.filter {
                LocalDate.ofEpochDay(it.dateEpochDay) in period.start..period.end
            }
            val salaryExpense = periodAttendance.sumOf { PixnetRules.staffSalary(it.staff) }
            val totalExpenses = nonPayrollExpenses + salaryExpense
            val netEarnings = income - totalExpenses

            val paidExpensesInPeriod = expenses
                .filter { it.paidEpochDay != null }
                .filter {
                    val paid = LocalDate.ofEpochDay(it.paidEpochDay!!)
                    paid in period.start..period.end
                }
                .sumOf { it.amount }

            val payrollPaidInPeriod = payrollPayments.filter {
                LocalDate.ofEpochDay(it.dateEpochDay) in period.start..period.end
            }.sumOf { it.amount }

            val advanceAdded = paidExpensesInPeriod + payrollPaidInPeriod
            val openingAdvance = ownerAdvance
            ownerAdvance += advanceAdded

            val contributionsInPeriod = ownerContributions.filter {
                LocalDate.ofEpochDay(it.dateEpochDay) in period.start..period.end
            }

            contributionsInPeriod.forEach { payment ->
                ownerCashContrib[payment.ownerName] =
                    (ownerCashContrib[payment.ownerName] ?: 0.0) + payment.amount
                ownerBalances[payment.ownerName] =
                    max(0.0, (ownerBalances[payment.ownerName] ?: 0.0) - payment.amount)
            }

            val ownerContributionCash = contributionsInPeriod.sumOf { it.amount }
            val fundsAvailable = cashCarry + income + ownerContributionCash

            val ownerAdvanceRepaid = if (isFinal) min(ownerAdvance, fundsAvailable) else 0.0
            ownerAdvance = max(0.0, ownerAdvance - ownerAdvanceRepaid)

            val contributionNeeded = if (isFinal) max(0.0, -netEarnings) else 0.0
            val contributionPerOwner =
                if (contributionNeeded > 0.0) contributionNeeded / PixnetRules.OWNERS.size else 0.0

            if (contributionPerOwner > 0.0) {
                PixnetRules.OWNERS.forEach { owner ->
                    ownerBalances[owner] = (ownerBalances[owner] ?: 0.0) + contributionPerOwner
                    ownerNewDue[owner] = (ownerNewDue[owner] ?: 0.0) + contributionPerOwner
                }
            }

            val unpaidBillsReserve = if (isFinal) {
                expenses.filter {
                    val incurred = LocalDate.ofEpochDay(it.incurredEpochDay)
                    val paid = it.paidEpochDay?.let(LocalDate::ofEpochDay)
                    !incurred.isAfter(period.end) && (paid == null || paid.isAfter(period.end))
                }.sumOf { it.amount }
            } else 0.0

            val unpaidPayrollReserve = if (isFinal) {
                PixnetRules.STAFF.sumOf { staff ->
                    val earned = attendance.filter {
                        it.staff == staff && !LocalDate.ofEpochDay(it.dateEpochDay).isAfter(period.end)
                    }.sumOf { PixnetRules.staffSalary(it.staff) }
                    val paid = payrollPayments.filter {
                        it.staff == staff && !LocalDate.ofEpochDay(it.dateEpochDay).isAfter(period.end)
                    }.sumOf { it.amount }
                    max(0.0, earned - paid)
                }
            } else 0.0

            val availableAfterAdvance = max(
                0.0,
                fundsAvailable - ownerAdvanceRepaid - unpaidBillsReserve - unpaidPayrollReserve
            )

            val profitPool = if (isFinal && netEarnings > 0.0 && ownerAdvance <= 0.005) {
                min(netEarnings, availableAfterAdvance)
            } else 0.0

            val grossProfitShare =
                if (profitPool > 0.0) profitPool / PixnetRules.OWNERS.size else 0.0

            var profitOffsetTotal = 0.0
            var cashProfitTotal = 0.0

            if (grossProfitShare > 0.0) {
                PixnetRules.OWNERS.forEach { owner ->
                    val balance = ownerBalances[owner] ?: 0.0
                    val offset = min(balance, grossProfitShare)
                    val cashPayout = max(0.0, grossProfitShare - offset)
                    ownerBalances[owner] = max(0.0, balance - offset)
                    ownerOffsets[owner] = (ownerOffsets[owner] ?: 0.0) + offset
                    ownerCashProfit[owner] = (ownerCashProfit[owner] ?: 0.0) + cashPayout
                    profitOffsetTotal += offset
                    cashProfitTotal += cashPayout
                }
            }

            cashCarry = max(0.0, fundsAvailable - ownerAdvanceRepaid - cashProfitTotal)

            periodSummaries += PeriodSummary(
                period = period,
                isFinal = isFinal,
                income = income,
                nonPayrollExpenses = nonPayrollExpenses,
                salaryExpense = salaryExpense,
                totalExpenses = totalExpenses,
                netEarnings = netEarnings,
                openingOwnerAdvance = openingAdvance,
                ownerAdvanceAdded = advanceAdded,
                ownerContributionsReceived = ownerContributionCash,
                fundsAvailable = fundsAvailable,
                ownerAdvanceRepaid = ownerAdvanceRepaid,
                endingOwnerAdvance = ownerAdvance,
                ownerContributionNeeded = contributionNeeded,
                contributionPerOwner = contributionPerOwner,
                profitPool = profitPool,
                grossProfitSharePerOwner = grossProfitShare,
                profitOffsetTotal = profitOffsetTotal,
                cashProfitPayoutTotal = cashProfitTotal,
                cashCarryForward = cashCarry,
                unpaidBillsReserve = unpaidBillsReserve,
                unpaidPayrollReserve = unpaidPayrollReserve
            )
        }

        val ownerSummaries = PixnetRules.OWNERS.map { owner ->
            val outstanding = ownerBalances[owner] ?: 0.0
            OwnerSummary(
                name = owner,
                openingBalance = PixnetRules.OPENING_OWNER_BALANCES[owner] ?: 0.0,
                newContributionsDue = ownerNewDue[owner] ?: 0.0,
                cashContributions = ownerCashContrib[owner] ?: 0.0,
                profitShareOffset = ownerOffsets[owner] ?: 0.0,
                cashProfitPaid = ownerCashProfit[owner] ?: 0.0,
                outstandingBalance = outstanding
            )
        }

        val settlementByDate = periodSummaries
            .filter { it.isFinal }
            .associateBy { it.period.end }

        val daily = mutableListOf<DailyBalance>()
        var runningEarnings = 0.0
        var runningCash = PixnetRules.OPENING_CASH

        if (!effectiveToday.isBefore(PixnetRules.START_DATE)) {
            var date = PixnetRules.START_DATE
            while (!date.isAfter(effectiveToday)) {
                val income = collections.filter {
                    LocalDate.ofEpochDay(it.dateEpochDay) == date
                }.sumOf { it.pisonet + it.printer + it.otherIncome }

                val expensesIncurred = expenses.filter {
                    LocalDate.ofEpochDay(it.incurredEpochDay) == date
                }.sumOf { it.amount }

                val payrollIncurred = attendance.filter {
                    LocalDate.ofEpochDay(it.dateEpochDay) == date
                }.sumOf { PixnetRules.staffSalary(it.staff) }

                val netMovement = income - expensesIncurred - payrollIncurred
                runningEarnings += netMovement

                val ownerContribIn = ownerContributions.filter {
                    LocalDate.ofEpochDay(it.dateEpochDay) == date
                }.sumOf { it.amount }

                val settlement = settlementByDate[date]
                val ownerAdvanceRepaid = settlement?.ownerAdvanceRepaid ?: 0.0
                val cashProfitOut = settlement?.cashProfitPayoutTotal ?: 0.0

                val cashMovement = income + ownerContribIn - ownerAdvanceRepaid - cashProfitOut
                runningCash += cashMovement

                daily += DailyBalance(
                    date = date,
                    income = income,
                    expensesIncurred = expensesIncurred,
                    payrollIncurred = payrollIncurred,
                    netEarningsMovement = netMovement,
                    runningNetEarnings = runningEarnings,
                    ownerContributionsIn = ownerContribIn,
                    ownerAdvanceRepaid = ownerAdvanceRepaid,
                    cashProfitShareOut = cashProfitOut,
                    pixnetCashMovement = cashMovement,
                    pixnetCashBalance = runningCash
                )
                date = date.plusDays(1)
            }
        }

        val currentPeriod = periodSummaries.firstOrNull {
            effectiveToday in it.period.start..it.period.end
        } ?: periodSummaries.lastOrNull()

        val unpaidBillsNow = expenses.filter {
            val incurred = LocalDate.ofEpochDay(it.incurredEpochDay)
            val paid = it.paidEpochDay?.let(LocalDate::ofEpochDay)
            !incurred.isAfter(effectiveToday) && (paid == null || paid.isAfter(effectiveToday))
        }.sumOf { it.amount }

        val staffPayrollSummaries = PixnetRules.STAFF.map { staff ->
            val attendanceRows = attendance.filter {
                it.staff == staff && !LocalDate.ofEpochDay(it.dateEpochDay).isAfter(effectiveToday)
            }
            val salaryEarned = attendanceRows.sumOf { PixnetRules.staffSalary(it.staff) }
            val salaryPaid = payrollPayments.filter {
                it.staff == staff && !LocalDate.ofEpochDay(it.dateEpochDay).isAfter(effectiveToday)
            }.sumOf { it.amount }
            StaffPayrollSummary(
                name = staff,
                daysWorked = attendanceRows.size,
                salaryEarned = salaryEarned,
                salaryPaid = salaryPaid,
                outstandingBalance = max(0.0, salaryEarned - salaryPaid)
            )
        }

        val outstandingPayrollNow = staffPayrollSummaries.sumOf { it.outstandingBalance }

        val paidExpensesNow = expenses.filter {
            it.paidEpochDay?.let(LocalDate::ofEpochDay)?.let { paid ->
                !paid.isAfter(effectiveToday)
            } ?: false
        }.sumOf { it.amount }

        val payrollPaidNow = payrollPayments.filter {
            !LocalDate.ofEpochDay(it.dateEpochDay).isAfter(effectiveToday)
        }.sumOf { it.amount }

        val settledAdvanceToDate = periodSummaries
            .filter { it.isFinal && !it.period.end.isAfter(effectiveToday) }
            .sumOf { it.ownerAdvanceRepaid }

        val currentOwnerAdvance = max(
            0.0,
            PixnetRules.OPENING_OWNER_ADVANCE + paidExpensesNow + payrollPaidNow - settledAdvanceToDate
        )

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
            currentOwnerAdvance = currentOwnerAdvance,
            currentPixnetCash = daily.lastOrNull()?.pixnetCashBalance ?: PixnetRules.OPENING_CASH,
            outstandingPayroll = outstandingPayrollNow,
            unpaidBills = unpaidBillsNow
        )
    }
}
