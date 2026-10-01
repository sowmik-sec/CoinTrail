package com.cointrail.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Tap-to-edit date, for back-filling an expense on an earlier day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showDialog = true }, modifier = modifier) {
        Text(date.format(DATE_FORMAT))
    }
    if (showDialog) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        showDialog = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Tap-to-edit time of day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    time: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showDialog = true }, modifier = modifier) {
        Text(time.format(TIME_FORMAT))
    }
    if (showDialog) {
        val pickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeSelected(LocalTime.of(pickerState.hour, pickerState.minute))
                        showDialog = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
            text = { TimePicker(state = pickerState) },
        )
    }
}
