package com.cointrail.data.alerts

import android.content.Context
import com.cointrail.domain.budget.BudgetAlertKey
import com.cointrail.domain.budget.BudgetAlertLevel
import java.time.YearMonth

/**
 * Remembers which budget thresholds have already been alerted in a given month. This is
 * device-local notification bookkeeping, not user data: it never syncs and is keyed by month so
 * alerts re-arm automatically when the month rolls over (SPEC §6.5).
 */
interface BudgetAlertStore {

    suspend fun firedFor(month: YearMonth): Set<BudgetAlertKey>

    suspend fun markFired(month: YearMonth, keys: Set<BudgetAlertKey>)
}

/** Stores fired thresholds as a string set per month in shared preferences. */
class SharedPreferencesBudgetAlertStore(context: Context) : BudgetAlertStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun firedFor(month: YearMonth): Set<BudgetAlertKey> =
        prefs.getStringSet(prefsKey(month), emptySet()).orEmpty().mapNotNull(::decode).toSet()

    override suspend fun markFired(month: YearMonth, keys: Set<BudgetAlertKey>) {
        if (keys.isEmpty()) return
        val current = prefs.getStringSet(prefsKey(month), emptySet()).orEmpty()
        prefs.edit().putStringSet(prefsKey(month), current + keys.map(::encode)).apply()
        pruneBefore(month)
    }

    private fun pruneBefore(month: YearMonth) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(PREFIX) }
            .filter { stored -> monthOf(stored)?.let { it < month } == true }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    private fun monthOf(prefKey: String): YearMonth? =
        runCatching { YearMonth.parse(prefKey.removePrefix(PREFIX)) }.getOrNull()

    private fun prefsKey(month: YearMonth): String = "$PREFIX$month"

    private fun encode(key: BudgetAlertKey): String = "${key.scopeKey}$SEPARATOR${key.level.name}"

    private fun decode(raw: String): BudgetAlertKey? {
        val separator = raw.lastIndexOf(SEPARATOR)
        if (separator < 0) return null
        val scopeKey = raw.substring(0, separator).ifEmpty { return null }
        val level = runCatching { BudgetAlertLevel.valueOf(raw.substring(separator + 1)) }.getOrNull()
            ?: return null
        return BudgetAlertKey(scopeKey, level)
    }

    private companion object {
        const val PREFS_NAME = "cointrail_budget_alerts"
        const val PREFIX = "fired."
        const val SEPARATOR = "|"
    }
}
