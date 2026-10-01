package com.cointrail.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cointrail.domain.budget.BudgetProgress
import com.cointrail.domain.budget.BudgetStatus

/** A titled stack of budget progress bars, shared by Today and the monthly reports screen. */
@Composable
fun BudgetProgressSection(
    title: String,
    budgets: List<BudgetProgress>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        budgets.forEach { BudgetBar(it) }
    }
}

@Composable
private fun BudgetBar(progress: BudgetProgress) {
    val barColor = when (progress.status) {
        BudgetStatus.ON_TRACK -> MaterialTheme.colorScheme.primary
        BudgetStatus.WARNING -> MaterialTheme.colorScheme.tertiary
        BudgetStatus.EXCEEDED -> MaterialTheme.colorScheme.error
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = progress.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${progress.spent.format()} / ${progress.limit.format()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (progress.percent / 100f).coerceIn(0f, 1f) },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
    }
}
