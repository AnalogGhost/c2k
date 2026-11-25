package com.hackerapps.c2k.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.hackerapps.c2k.BuildConfig

/** The page a share links to; it covers Android (F-Droid and Play) and iOS. */
const val WEBSITE_URL = "https://hackerapps.com/apps/c2k/"

/**
 * Opens C2K's page in the store this build is from (Google Play or F-Droid), falling back to the
 * store's web page in a browser. The store app or browser does any networking; C2K needs none.
 */
fun Context.openStorePage() {
    for (url in listOf(BuildConfig.STORE_URL, BuildConfig.STORE_WEB_URL)) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        }
    }
}

/** Opens the share sheet with [text]. */
fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        // Nothing on the device can share text.
    }
}
