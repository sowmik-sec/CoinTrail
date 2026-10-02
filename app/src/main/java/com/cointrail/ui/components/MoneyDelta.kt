package com.cointrail.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.cointrail.core.Money

/** "+৳1,250" when spending rose, "-৳1,250" when it fell; [Money.format] supplies the minus sign. */
fun formatDelta(delta: Money): String =
    if (delta.paisa > 0) "+${delta.format()}" else delta.format()

/** Spending up is the alarming direction (red), down is the calm one (teal). */
@Composable
fun deltaColor(delta: Money): Color = when {
    delta.paisa > 0 -> MaterialTheme.colorScheme.error
    delta.paisa < 0 -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
