package com.dallet.app.ui.common

import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Picks two dates; one tapped date means a one-day range. Both ends are inclusive. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    initialFrom: LocalDate?,
    initialTo: LocalDate?,
    onConfirm: (from: LocalDate, to: LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom?.toPickerMillis(),
        initialSelectedEndDateMillis = initialTo?.toPickerMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    val end = state.selectedEndDateMillis ?: start
                    onConfirm(start.fromPickerMillis(), end.fromPickerMillis())
                }
            ) {
                Text(stringResource(R.string.date_picker_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.date_picker_cancel))
            }
        }
    ) {
        val locale = LocalConfiguration.current.locales[0]
        val format = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        val start = state.selectedStartDateMillis?.fromPickerMillis()
        val end = state.selectedEndDateMillis?.fromPickerMillis()
        val rangeText = when {
            start == null -> stringResource(R.string.date_range_hint)
            end == null || end == start -> start.format(format)
            else -> "${start.format(format)} — ${end.format(format)}"
        }
        DateRangePicker(
            state = state,
            modifier = Modifier.height(500.dp),
            // The stock headline puts both dates in narrow columns and wraps them letter by letter.
            headline = {
                Text(
                    text = rangeText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            },
            showModeToggle = false
        )
    }
}

private fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.fromPickerMillis(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
