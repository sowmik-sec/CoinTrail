package com.cointrail.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Entry controls shared by quick-add and edit so an amount is entered the same way on both. */
data class ChipOption(val id: String, val name: String)

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun AmountDisplay(display: String, hasInput: Boolean) {
    val caretTransition = rememberInfiniteTransition(label = "amount-caret")
    val caretAlpha by caretTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 500), RepeatMode.Reverse),
        label = "amount-caret-alpha",
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        SectionLabel("Amount")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = display,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (hasInput) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.width(3.dp))
            Box(
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .width(3.dp)
                    .height(36.dp)
                    .alpha(caretAlpha)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(
    options: List<ChipOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.id == selectedId,
                onClick = { onSelect(option.id) },
                label = { Text(option.name) },
            )
        }
    }
}

@Composable
fun NoteField(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    if (expanded) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            label = { Text("Note") },
            singleLine = true,
            modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    } else {
        TextButton(onClick = onToggle, modifier = modifier) { Text("Add note") }
    }
}

private val KEYPAD_ROWS: List<List<String>> = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(".", "0", "⌫"),
)

@Composable
fun AmountKeypad(
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        KEYPAD_ROWS.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    TextButton(
                        onClick = {
                            when (key) {
                                "." -> onDecimal()
                                "⌫" -> onBackspace()
                                else -> onDigit(key[0])
                            }
                        },
                        modifier = Modifier.weight(1f).height(60.dp),
                    ) {
                        Text(
                            text = key,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
