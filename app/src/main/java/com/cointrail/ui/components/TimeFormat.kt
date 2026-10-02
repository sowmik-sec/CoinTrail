package com.cointrail.ui.components

import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clock format for expense-row times, honoring the device's 12/24-hour setting (SPEC §4). */
fun timeFormat(is24Hour: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.ENGLISH)
