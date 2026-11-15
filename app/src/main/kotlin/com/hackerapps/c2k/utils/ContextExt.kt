package com.hackerapps.c2k.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate

/** This context with its resources in [locale]. */
fun Context.withLocale(locale: java.util.Locale): Context =
    createConfigurationContext(Configuration(resources.configuration).apply { setLocale(locale) })

fun Context.localized(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return this

    val config = Configuration(resources.configuration)
    config.setLocales(locales.unwrap() as LocaleList)
    return createConfigurationContext(config)
}
