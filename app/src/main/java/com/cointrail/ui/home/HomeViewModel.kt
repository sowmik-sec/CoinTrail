package com.cointrail.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.Money
import com.cointrail.data.alerts.BudgetAlertTracker
import com.cointrail.data.repo.BudgetStore
import com.cointrail.data.repo.CategoryStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.data.repo.PaymentMethodStore
import com.cointrail.domain.budget.BudgetProgress
import com.cointrail.domain.budget.BudgetProgressCalculator
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.CategoryTotal
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.reports.MonthSummary
import com.cointrail.ui.components.ExpenseRowUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Home state (SPEC §6.11): the month-to-date hero with its month-over-month move, the today block
 * (total, entry count and the last few entries), the budget bars and the first-run flag.
 */
data class HomeUiState(
    val month: YearMonth = YearMonth.now(),
    val monthTotal: Money = Money.ZERO,
    val momDelta: Money? = null,
    val momPercent: Int? = null,
    val todayTotal: Money = Money.ZERO,
    val todayCount: Int = 0,
    val recent: List<ExpenseRowUi> = emptyList(),
    val budgets: List<BudgetProgress> = emptyList(),
    val hasAnyExpenses: Boolean = false,
)

/** Backs the Home screen: one composed snapshot of where the month and the day stand. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val expenses: ExpenseStore,
    categories: CategoryStore,
    paymentMethods: PaymentMethodStore,
    budgets: BudgetStore,
    budgetAlerts: BudgetAlertTracker,
    private val today: () -> LocalDate = { LocalDate.now() },
    timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm"),
) : ViewModel() {

    private val day = MutableStateFlow(today())

    val state: StateFlow<HomeUiState> = day.flatMapLatest { date ->
        val dayStart = date.atStartOfDay()
        val dayEnd = dayStart.plusDays(1)
        val monthStart = date.withDayOfMonth(1).atStartOfDay()
        val monthEnd = monthStart.plusMonths(1)
        combine(
            expenses.observeBetween(dayStart, dayEnd),
            expenses.observeTotalBetween(dayStart, dayEnd),
            expenses.observeTotalBetween(monthStart, monthEnd),
            expenses.observeTotalBetween(monthStart.minusMonths(1), monthStart),
            expenses.observeTotalBetween(ALL_TIME_START, ALL_TIME_END),
        ) { dayRows, dayTotal, monthTotal, previousTotal, allTotal ->
            Snapshot(dayRows, dayTotal, monthTotal, previousTotal, allTotal)
        }
            .combine(expenses.observeCategoryTotals(monthStart, monthEnd)) { snapshot, categoryTotals ->
                snapshot.copy(categoryTotals = categoryTotals)
            }
            .combine(categories.observeAll()) { snapshot, categoryList -> snapshot to categoryList }
            .combine(paymentMethods.observeAll()) { (snapshot, categoryList), paymentMethodList ->
                SnapshotWithCatalogs(snapshot, categoryList, paymentMethodList)
            }
            .combine(budgets.observeAll()) { (snapshot, categoryList, paymentMethodList), budgetList ->
                buildState(date, snapshot, categoryList, paymentMethodList, budgetList, timeFormat)
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        // Every change to month-to-date spending is a chance to cross a budget threshold.
        viewModelScope.launch {
            state.collect { ui -> budgetAlerts.evaluate(ui.budgets) }
        }
    }

    /** Re-anchors the screen to the current local day, e.g. when the app returns to the foreground. */
    fun refresh() {
        day.value = today()
    }

    private fun buildState(
        date: LocalDate,
        snapshot: Snapshot,
        categoryList: List<Category>,
        paymentMethodList: List<PaymentMethod>,
        budgetList: List<Budget>,
        timeFormat: DateTimeFormatter,
    ): HomeUiState {
        val categoryNames = categoryList.associate { it.id to it.name }
        val paymentMethodNames = paymentMethodList.associate { it.id to it.name }
        val summary = MonthSummary(currentTotal = snapshot.monthTotal, previousTotal = snapshot.previousTotal)
        return HomeUiState(
            month = YearMonth.from(date),
            monthTotal = snapshot.monthTotal,
            momDelta = summary.delta,
            momPercent = summary.percentChange,
            todayTotal = snapshot.dayTotal,
            todayCount = snapshot.dayRows.size,
            recent = snapshot.dayRows.take(RECENT_LIMIT).map { expense ->
                ExpenseRowUi(
                    id = expense.id,
                    amount = expense.amount,
                    categoryName = categoryNames[expense.categoryId] ?: expense.categoryId,
                    paymentMethodName = expense.paymentMethodId?.let { paymentMethodNames[it] },
                    note = expense.note,
                    time = expense.occurredAt.format(timeFormat),
                )
            },
            budgets = BudgetProgressCalculator.forMonth(
                budgets = budgetList,
                categories = categoryList,
                overallSpent = snapshot.monthTotal,
                spentByCategory = snapshot.categoryTotals.associate { it.categoryId to it.total },
                categoryNames = categoryNames,
            ),
            hasAnyExpenses = snapshot.allTotal > Money.ZERO,
        )
    }

    private data class Snapshot(
        val dayRows: List<Expense>,
        val dayTotal: Money,
        val monthTotal: Money,
        val previousTotal: Money,
        val allTotal: Money,
        val categoryTotals: List<CategoryTotal> = emptyList(),
    )

    private data class SnapshotWithCatalogs(
        val snapshot: Snapshot,
        val categories: List<Category>,
        val paymentMethods: List<PaymentMethod>,
    )

    private companion object {
        const val RECENT_LIMIT = 5

        // A window wide enough to hold any plausible expense date, for the first-run check.
        val ALL_TIME_START: LocalDateTime = LocalDateTime.of(1900, 1, 1, 0, 0)
        val ALL_TIME_END: LocalDateTime = LocalDateTime.of(2100, 1, 1, 0, 0)
    }
}
