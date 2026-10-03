package com.cointrail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointrail.core.Money
import com.cointrail.ui.theme.Bricolage

/** One expense as shown in any list: category, then time · payment method · note, then amount. */
data class ExpenseRowUi(
    val id: String,
    val amount: Money,
    val categoryName: String,
    val paymentMethodName: String?,
    val note: String?,
    val time: String,
)

@Composable
fun ExpenseRow(row: ExpenseRowUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(row.categoryName)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.categoryName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val secondary = buildList {
                add(row.time)
                row.paymentMethodName?.let { add(it) }
                row.note?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" · ")
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = row.amount.format(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** The category's initial on a soft tile, so rows read as separate without divider lines. */
@Composable
private fun CategoryTile(categoryName: String) {
    val initial = categoryName.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            fontFamily = Bricolage,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
