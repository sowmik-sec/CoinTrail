package com.cointrail.ui.components

import com.cointrail.domain.model.CatalogItem
import com.cointrail.domain.model.Expense
import java.time.format.DateTimeFormatter

/**
 * The one projection of expenses plus the catalogue (ticket 27): what a row displays — the
 * category, the payment-method name, the note, the time and the amount — and the id-to-name
 * lookups the lists and breakdowns consume. Home and the Day screen build their rows with the
 * same [toRow], so two lists can never render the same expense differently.
 */

/** The id-to-name lookup a list of catalog items serves; declare it once, consume it everywhere. */
fun List<CatalogItem>.namesById(): Map<String, String> = associate { it.id to it.name }

/** A catalog id's name, falling back to the raw id when the list no longer carries it. */
fun Map<String, String>.nameOf(id: String): String = this[id] ?: id

/** Builds the display row for [expense] against the two catalog name lookups. */
fun Expense.toRow(
    categoryNames: Map<String, String>,
    paymentMethodNames: Map<String, String>,
    timeFormat: DateTimeFormatter,
): ExpenseRowUi = ExpenseRowUi(
    id = id,
    amount = amount,
    categoryName = categoryNames.nameOf(categoryId),
    paymentMethodName = paymentMethodId?.let(paymentMethodNames::get),
    note = note,
    time = occurredAt.format(timeFormat),
)
