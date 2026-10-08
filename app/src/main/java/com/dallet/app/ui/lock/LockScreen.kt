package com.dallet.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.dallet.app.ui.LockUiState

@Composable
fun LockScreen(
    state: LockUiState,
    biometricEnabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometricSuccess: () -> Unit,
    onResetConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val promptTitle = stringResource(R.string.lock_biometric_title)
    val cancelText = stringResource(R.string.dialog_cancel)
    val canUseBiometric = remember(biometricEnabled) {
        biometricEnabled && BiometricAuth.isAvailable(context)
    }
    val showPrompt = {
        BiometricAuth.prompt(context, promptTitle, cancelText, onBiometricSuccess)
    }
    var showReset by remember { mutableStateOf(false) }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric) showPrompt()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        PinDots(length = PinLock.PIN_LENGTH, filled = state.input.length)
        Text(
            text = state.errorMessage.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .height(40.dp)
                .padding(top = 12.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        PinPad(
            onDigit = onDigit,
            onBackspace = onBackspace,
            showFingerprint = canUseBiometric,
            onFingerprintClick = { showPrompt() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { showReset = true }) {
            Text(
                text = stringResource(R.string.lock_forgot),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(stringResource(R.string.reset_dialog_title)) },
            text = { Text(stringResource(R.string.lock_reset_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReset = false
                        onResetConfirmed()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.reset_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}
