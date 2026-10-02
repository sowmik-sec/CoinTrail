package com.cointrail.data.recurring

import com.cointrail.data.db.ExpenseDao
import com.cointrail.data.db.ExpenseEntity
import com.cointrail.data.db.RecurringSeriesDao
import com.cointrail.data.repo.toDomain
import com.cointrail.data.repo.toEntity
import com.cointrail.domain.recurring.RecurringGeneration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Creates the ordinary expenses for monthly recurring series (SPEC §6.7).
 *
 * Generation is idempotent: each occurrence has a stable id derived from the series and month, so a
 * re-run neither duplicates nor overwrites it. The guard is [ExpenseDao.byId], which deliberately
 * includes tombstoned rows (unlike the live queries), so editing or deleting a generated expense
 * never makes it reappear, and deleting a series leaves its expenses in place.
 */
class RecurringExpenseGenerator(
    private val seriesDao: RecurringSeriesDao,
    private val expenseDao: ExpenseDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {

    /**
     * Generates every occurrence that is due as of [today] (default: today). Returns how many
     * expenses were created. Safe to call on app open and from the background job.
     */
    suspend fun generateDue(today: LocalDate = now().toLocalDate()): Int {
        val timestamp = now()
        var generated = 0
        for (entity in seriesDao.active()) {
            val series = entity.toDomain()
            val month = RecurringGeneration.dueMonth(series, today) ?: continue
            val date = RecurringGeneration.occurrenceDate(series.dayOfMonth, month)
            val id = occurrenceId(series.id, month)
            if (expenseDao.byId(id) == null) {
                expenseDao.upsert(
                    ExpenseEntity(
                        id = id,
                        amountPaisa = series.amount.paisa,
                        categoryId = series.categoryId,
                        note = series.note,
                        paymentMethodId = series.paymentMethodId,
                        occurredAt = date.atStartOfDay(),
                        createdAt = timestamp,
                        updatedAt = timestamp,
                        deletedAt = null,
                    )
                )
                generated++
            }
            // Advance the generation marker without touching updatedAt: generation is bookkeeping,
            // not a user edit, so it must not win last-write-wins against a pause or edit synced
            // from another device (SPEC §7).
            seriesDao.upsert(series.copy(lastGeneratedMonth = month).toEntity())
        }
        return generated
    }

    companion object {

        /** The stable id of a series' occurrence in [month]; makes generation idempotent. */
        fun occurrenceId(seriesId: String, month: YearMonth): String = "recurring-$seriesId-$month"
    }
}
