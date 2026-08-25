package com.pixnet.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixnet.tracker.data.*
import com.pixnet.tracker.model.BusinessCalculator
import com.pixnet.tracker.model.PixnetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.max

private data class RawData(
    val collections: List<CollectionEntity>,
    val expenses: List<ExpenseEntity>,
    val attendance: List<AttendanceEntity>,
    val payrollPayments: List<PayrollPaymentEntity>,
    val ownerContributions: List<OwnerContributionEntity>
)

class PixnetViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = PixnetDatabase.get(application).dao()

    private val rawData: Flow<RawData> = combine(
        dao.observeCollections(),
        dao.observeExpenses(),
        dao.observeAttendance(),
        dao.observePayrollPayments(),
        dao.observeOwnerContributions()
    ) { collections, expenses, attendance, payroll, ownerContrib ->
        RawData(collections, expenses, attendance, payroll, ownerContrib)
    }

    private val clock: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }

    val state: StateFlow<PixnetState> = combine(rawData, clock) { raw, today ->
        BusinessCalculator.calculate(
            collections = raw.collections,
            expenses = raw.expenses,
            attendance = raw.attendance,
            payrollPayments = raw.payrollPayments,
            ownerContributions = raw.ownerContributions,
            today = today
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PixnetState()
    )

    fun addCollection(date: LocalDate, pisonet: Double, printer: Double, other: Double) {
        viewModelScope.launch {
            dao.insertCollection(
                CollectionEntity(
                    dateEpochDay = date.toEpochDay(),
                    pisonet = pisonet,
                    printer = printer,
                    otherIncome = other
                )
            )
        }
    }

    fun deleteCollection(entry: CollectionEntity) {
        viewModelScope.launch { dao.deleteCollection(entry) }
    }

    fun addExpense(
        date: LocalDate,
        category: String,
        description: String,
        amount: Double,
        paidDate: LocalDate?,
        notes: String
    ) {
        viewModelScope.launch {
            dao.insertExpense(
                ExpenseEntity(
                    incurredEpochDay = date.toEpochDay(),
                    category = category,
                    description = description,
                    amount = amount,
                    paidEpochDay = paidDate?.toEpochDay(),
                    notes = notes
                )
            )
        }
    }

    fun markExpensePaid(entry: ExpenseEntity, paidDate: LocalDate) {
        viewModelScope.launch {
            dao.updateExpense(entry.copy(paidEpochDay = paidDate.toEpochDay()))
        }
    }

    fun deleteExpense(entry: ExpenseEntity) {
        viewModelScope.launch { dao.deleteExpense(entry) }
    }

    fun setAttendance(date: LocalDate, staff: String) {
        viewModelScope.launch {
            dao.setAttendance(AttendanceEntity(date.toEpochDay(), staff))
        }
    }

    fun clearAttendance(date: LocalDate) {
        viewModelScope.launch { dao.clearAttendance(date.toEpochDay()) }
    }

    fun addPayrollPayment(date: LocalDate, staff: String, amount: Double, reference: String): Boolean {
        val snapshot = state.value
        val earned = snapshot.attendance.filter {
            it.staff == staff && LocalDate.ofEpochDay(it.dateEpochDay) <= date
        }.sumOf { com.pixnet.tracker.model.PixnetRules.staffSalary(it.staff) }
        val paid = snapshot.payrollPayments.filter {
            it.staff == staff && LocalDate.ofEpochDay(it.dateEpochDay) <= date
        }.sumOf { it.amount }
        val outstanding = max(0.0, earned - paid)

        if (amount <= 0.0 || amount > outstanding + 0.005) return false

        viewModelScope.launch {
            dao.insertPayrollPayment(
                PayrollPaymentEntity(
                    dateEpochDay = date.toEpochDay(),
                    staff = staff,
                    amount = amount,
                    reference = reference
                )
            )
        }
        return true
    }

    fun deletePayrollPayment(entry: PayrollPaymentEntity) {
        viewModelScope.launch { dao.deletePayrollPayment(entry) }
    }

    fun addOwnerContribution(date: LocalDate, owner: String, amount: Double, reference: String) {
        viewModelScope.launch {
            dao.insertOwnerContribution(
                OwnerContributionEntity(
                    dateEpochDay = date.toEpochDay(),
                    ownerName = owner,
                    amount = amount,
                    reference = reference
                )
            )
        }
    }

    fun deleteOwnerContribution(entry: OwnerContributionEntity) {
        viewModelScope.launch { dao.deleteOwnerContribution(entry) }
    }
}
