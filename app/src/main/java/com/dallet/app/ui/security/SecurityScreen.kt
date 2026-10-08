package com.dallet.app.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.security.BiometricAuth
import com.dallet.app.security.PinLock
import com.dallet.app.ui.SecurityUiState
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.lock.PinDots
import com.dallet.app.ui.lock.PinPad

private enum class SecurityStep {
    IDLE,
    BACKUP_HINT,
    ENTER_NEW,
    CONFIRM_NEW,
    DISABLE
}

@Composable
fun SecurityScreen(
    state: SecurityUiState,
    onExportClick: () -> Unit,
    onEnablePin: (String) -> Unit,
    onDisablePin: (String, (Boolean) -> Unit) -> Unit,
    onBiometricChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val biometricAvailable = remember { BiometricAuth.isAvailable(context) }
    val promptTitle = stringResource(R.string.lock_biometric_title)
    val cancelText = stringResource(R.string.dialog_cancel)
    val mismatchText = stringResource(R.string.security_mismatch)
    val wrongText = stringResource(R.string.security_wrong)

    var step by remember { mutableStateOf(SecurityStep.IDLE) }
    var firstPin by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun backToIdle() {
        step = SecurityStep.IDLE
        firstPin = ""
        input = ""
        error = null
    }

    fun onDigit(digit: Char) {
        if (input.length >= PinLock.PIN_LENGTH) return
        input += digit
        error = null
        if (input.length < PinLock.PIN_LENGTH) return
        when (step) {
            SecurityStep.ENTER_NEW -> {
                firstPin = input
                input = ""
                step = SecurityStep.CONFIRM_NEW
            }
            SecurityStep.CONFIRM_NEW -> {
                if (input == firstPin) {
                    onEnablePin(input)
                    backToIdle()
                } else {
                    error = mismatchText
                    firstPin = ""
                    input = ""
                    step = SecurityStep.ENTER_NEW
                }
            }
            SecurityStep.DISABLE -> {
                val entered = input
                input = ""
                onDisablePin(entered) { ok ->
                    if (ok) backToIdle() else error = wrongText
                }
            }
            else -> Unit
        }
    }

    if (step == SecurityStep.ENTER_NEW ||
        step == SecurityStep.CONFIRM_NEW ||
        step == SecurityStep.DISABLE
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(
                    when (step) {
                        SecurityStep.ENTER_NEW -> R.string.security_enter_new
                        SecurityStep.CONFIRM_NEW -> R.string.security_confirm_new
                        else -> R.string.security_enter_current
                    }
                ),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(24.dp))
            PinDots(length = PinLock.PIN_LENGTH, filled = input.length)
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .height(40.dp)
                    .padding(top = 12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            PinPad(
                onDigit = { digit -> onDigit(digit) },
                onBackspace = {
                    input = input.dropLast(1)
                    error = null
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { backToIdle() }) {
                Text(
                    text = stringResource(R.string.dialog_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.security_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SurfaceCard {
            SwitchRow(
                title = stringResource(R.string.security_pin),
                hint = stringResource(R.string.security_pin_hint),
                checked = state.pinEnabled,
                onCheckedChange = { enabled ->
                    step = if (enabled) SecurityStep.BACKUP_HINT else SecurityStep.DISABLE
                }
            )
        }
        if (state.pinEnabled && biometricAvailable) {
            SurfaceCard {
                SwitchRow(
                    title = stringResource(R.string.security_biometric),
                    hint = stringResource(R.string.security_biometric_hint),
                    checked = state.biometricEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            BiometricAuth.prompt(context, promptTitle, cancelText) {
                                onBiometricChange(true)
                            }
                        } else {
                            onBiometricChange(false)
                        }
                    }
                )
            }
        }
    }

    if (step == SecurityStep.BACKUP_HINT) {
        AlertDialog(
            onDismissRequest = { backToIdle() },
            title = { Text(stringResource(R.string.security_backup_title)) },
            text = { Text(stringResource(R.string.security_backup_text)) },
            confirmButton = {
                TextButton(onClick = { step = SecurityStep.ENTER_NEW }) {
                    Text(stringResource(R.string.security_backup_continue))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            backToIdle()
                            onExportClick()
                        }
                    ) {
                        Text(stringResource(R.string.security_backup_export))
                    }
                    TextButton(onClick = { backToIdle() }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
            }
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
