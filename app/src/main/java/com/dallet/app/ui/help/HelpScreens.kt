package com.dallet.app.ui.help

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dallet.app.R
import com.dallet.app.ui.common.AppButtonShape
import com.dallet.app.ui.common.SurfaceCard

/** Where feedback goes. The screen never prints it, but the mail app shows it in the To field. */
private const val FEEDBACK_ADDRESS = "dalletwallet@gmail.com"

private class FaqEntry(@StringRes val question: Int, @StringRes val answer: Int)

private val FAQ = listOf(
    FaqEntry(R.string.faq_q1, R.string.faq_a1),
    FaqEntry(R.string.faq_q2, R.string.faq_a2),
    FaqEntry(R.string.faq_q3, R.string.faq_a3),
    FaqEntry(R.string.faq_q4, R.string.faq_a4),
    FaqEntry(R.string.faq_q5, R.string.faq_a5),
    FaqEntry(R.string.faq_q6, R.string.faq_a6),
    FaqEntry(R.string.faq_q7, R.string.faq_a7),
    FaqEntry(R.string.faq_q8, R.string.faq_a8),
    FaqEntry(R.string.faq_q9, R.string.faq_a9),
    FaqEntry(R.string.faq_q10, R.string.faq_a10),
    FaqEntry(R.string.faq_q11, R.string.faq_a11),
    FaqEntry(R.string.faq_q12, R.string.faq_a12)
)

@Composable
fun FaqScreen() {
    var openIndex by remember { mutableStateOf(-1) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.faq_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        FAQ.forEachIndexed { index, entry ->
            val open = openIndex == index
            SurfaceCard(onClick = { openIndex = if (open) -1 else index }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(entry.question),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (open) {
                    Text(
                        text = stringResource(entry.answer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

private enum class FeedbackKind(@StringRes val label: Int, @StringRes val subject: Int) {
    COMPLAINT(R.string.feedback_kind_complaint, R.string.feedback_subject_complaint),
    IDEA(R.string.feedback_kind_idea, R.string.feedback_subject_idea),
    QUESTION(R.string.feedback_kind_question, R.string.feedback_subject_question)
}

@Composable
fun FeedbackScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var kind by remember { mutableStateOf(FeedbackKind.IDEA) }
    var text by remember { mutableStateOf("") }
    var withInfo by remember { mutableStateOf(true) }
    var noMailApp by remember { mutableStateOf(false) }
    var waitingForMail by remember { mutableStateOf(false) }
    var askSent by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Back from the mail app: the message may or may not have been sent, so ask.
            if (event == Lifecycle.Event.ON_RESUME && waitingForMail) {
                waitingForMail = false
                askSent = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val subject = stringResource(kind.subject)
    val infoLine = stringResource(
        R.string.feedback_info_line,
        versionName,
        Build.VERSION.RELEASE,
        Build.MODEL
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.feedback_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedbackKind.values().forEach { option ->
                FilterChip(
                    selected = kind == option,
                    onClick = { kind = option },
                    label = { Text(stringResource(option.label)) }
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                noMailApp = false
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 7,
            placeholder = { Text(stringResource(R.string.feedback_hint)) }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = withInfo, onCheckedChange = { withInfo = it })
            Text(
                text = stringResource(R.string.feedback_with_info),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (noMailApp) {
            Text(
                text = stringResource(R.string.feedback_no_mail),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Button(
            shape = AppButtonShape,
            onClick = {
                val body = if (withInfo) text.trim() + "\n\n" + infoLine else text.trim()
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_ADDRESS))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                try {
                    waitingForMail = true
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    waitingForMail = false
                    noMailApp = true
                }
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.feedback_send))
        }
        Text(
            text = stringResource(R.string.feedback_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (askSent) {
        AlertDialog(
            onDismissRequest = { askSent = false },
            title = { Text(stringResource(R.string.feedback_sent_title)) },
            text = { Text(stringResource(R.string.feedback_sent_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askSent = false
                        text = ""
                        onDone()
                    }
                ) {
                    Text(stringResource(R.string.feedback_sent_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { askSent = false }) {
                    Text(stringResource(R.string.feedback_sent_no))
                }
            }
        )
    }
}

