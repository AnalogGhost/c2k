package com.hackerapps.c2k.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate

fun Context.localized(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return this

    val config = Configuration(resources.configuration)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        config.setLocales(locales.unwrap() as LocaleList)
    } else {
        @Suppress("DEPRECATION")
        config.setLocale(locales.get(0))
    }
    return createConfigurationContext(config)
}
