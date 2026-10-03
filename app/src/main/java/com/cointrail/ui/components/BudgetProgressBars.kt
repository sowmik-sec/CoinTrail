package com.cointrail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.cointrail.domain.budget.BudgetProgress
import com.cointrail.domain.budget.BudgetStatus

/** A titled stack of budget progress bars, shared by Home and the monthly reports screen. */
@Composable
fun BudgetProgressSection(
    title: String,
    budgets: List<BudgetProgress>,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = title, style = titleStyle)
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
    val isOverLimit = progress.spent > progress.limit
    val standing = if (isOverLimit) {
        "${(progress.spent - progress.limit).format()} over ${progress.limit.format()}"
    } else {
        "${(progress.limit - progress.spent).format()} left of ${progress.limit.format()}"
    }
    val filledFraction = (progress.percent / 100f).coerceIn(0f, 1f)
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = progress.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = standing,
                style = MaterialTheme.typography.bodySmall,
                color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(filledFraction, 0f..1f) },
        ) {
            if (filledFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(filledFraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(barColor),
                )
            }
        }
    }
}
