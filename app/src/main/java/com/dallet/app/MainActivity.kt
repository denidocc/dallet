package com.dallet.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import java.util.Locale
import com.dallet.app.data.SharedPreferencesSettingsStore
import com.dallet.app.ledger.ThemeMode
import com.dallet.app.ui.DalletApp
import com.dallet.app.ui.theme.DalletTheme

class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val language = SharedPreferencesSettingsStore(newBase).language()
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language.tag))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DalletApplication
        setContent {
            var themeMode by remember { mutableStateOf(app.settings.themeMode()) }
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DalletTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    DalletApp(
                        ledger = app.ledger,
                        pinLock = app.pinLock,
                        shopping = app.shopping,
                        strings = app.strings,
                        themeMode = themeMode,
                        onThemeModeChange = {
                            app.settings.setThemeMode(it)
                            themeMode = it
                        },
                        language = app.settings.language(),
                        onLanguageChange = {
                            app.settings.setLanguage(it)
                            recreate()
                        }
                    )
                    CrashReportDialog()
                }
            }
        }
    }
}

@Composable
private fun CrashReportDialog() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var report by remember { mutableStateOf(CrashReporter.read(context)) }
    val text = report ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                Text(stringResource(R.string.crash_copy))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    CrashReporter.clear(context)
                    report = null
                }
            ) {
                Text(stringResource(R.string.crash_close))
            }
        }
    )
}
