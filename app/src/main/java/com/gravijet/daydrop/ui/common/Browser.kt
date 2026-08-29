package com.gravijet.daydrop.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens a source link, quietly doing nothing if the device has no browser. */
fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
