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

data class DeleteConfirmation(
    val title: String,
    val message: String,
    val confirmLabel: String = "Delete",
    val action: suspend () -> Unit
)

private data class RawData(
    val collections: List<CollectionEntity>,
    val expenses: List<ExpenseEntity>,
    val attendance: List<AttendanceEntity>,
    val payrollPayments: List<PayrollPaymentEntity>,
    val ownerContributions: List<OwnerContributionEntity>
)

class PixnetViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = PixnetDatabase.get(application).dao()

    private val _deleteConfirmation = MutableStateFlow<DeleteConfirmation?>(null)
    val deleteConfirmation: StateFlow<DeleteConfirmation?> = _deleteConfirmation.asStateFlow()

    private fun requestDelete(
        title: String,
        message: String,
        confirmLabel: String = "Delete",
        action: suspend () -> Unit
    ) {
        _deleteConfirmation.value = DeleteConfirmation(title, message, confirmLabel, action)
    }

    fun confirmDelete() {
        val pending = _deleteConfirmation.value ?: return
        _deleteConfirmation.value = null
        viewModelScope.launch { pending.action() }
    }

    fun cancelDelete() {
        _deleteConfirmation.value = null
    }

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
        requestDelete(
            title = "Delete collection record?",
            message = "This will permanently remove this collection and recalculate the shared owner balances."
        ) { dao.deleteCollection(entry) }
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
        requestDelete(
            title = "Delete expense record?",
            message = "This will permanently remove this bill. If it was already paid, the actual shared balance will also be recalculated."
        ) { dao.deleteExpense(entry) }
    }

    fun setAttendance(date: LocalDate, staff: String) {
        viewModelScope.launch {
            dao.setAttendance(AttendanceEntity(date.toEpochDay(), staff))
        }
    }

    fun clearAttendance(date: LocalDate) {
        requestDelete(
            title = "Clear attendance?",
            message = "This will remove the attendance record for $date and recalculate that staff member's payroll outstanding.",
            confirmLabel = "Clear"
        ) { dao.clearAttendance(date.toEpochDay()) }
    }

    fun addPayrollPayment(date: LocalDate, staff: String, amount: Double, reference: String): PayrollPaymentEntity? {
        val snapshot = state.value
        val earned = snapshot.attendance.filter {
            it.staff == staff && LocalDate.ofEpochDay(it.dateEpochDay) <= date
        }.sumOf { com.pixnet.tracker.model.PixnetRules.staffSalary(it.staff) }
        val paid = snapshot.payrollPayments.filter {
            it.staff == staff && LocalDate.ofEpochDay(it.dateEpochDay) <= date
        }.sumOf { it.amount }
        val outstanding = max(0.0, earned - paid)

        if (amount <= 0.0 || amount > outstanding + 0.005) return null

        val entry = PayrollPaymentEntity(
            dateEpochDay = date.toEpochDay(),
            staff = staff,
            amount = amount,
            reference = reference
        )

        viewModelScope.launch {
            dao.insertPayrollPayment(entry)
        }
        return entry
    }

    fun deletePayrollPayment(entry: PayrollPaymentEntity) {
        requestDelete(
            title = "Delete payroll payment?",
            message = "This will permanently remove the salary payment, make the amount outstanding again, and recalculate the actual shared balance."
        ) { dao.deletePayrollPayment(entry) }
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
        requestDelete(
            title = "Delete owner contribution?",
            message = "This will permanently remove the contribution and restore the amount to this owner's running balance."
        ) { dao.deleteOwnerContribution(entry) }
    }
}
