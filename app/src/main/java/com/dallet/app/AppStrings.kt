package com.dallet.app

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.dallet.app.ledger.SettingsStore
import java.util.Locale

/** Resolves string resources in the language chosen in settings (for ViewModels and PDF). */
class AppStrings(
    private val appContext: Context,
    private val settings: SettingsStore
) {
    val locale: Locale
        get() = Locale.forLanguageTag(settings.language().tag)

    fun get(@StringRes id: Int, vararg args: Any): String {
        val config = Configuration(appContext.resources.configuration)
        config.setLocale(locale)
        return appContext.createConfigurationContext(config).getString(id, *args)
    }
}
