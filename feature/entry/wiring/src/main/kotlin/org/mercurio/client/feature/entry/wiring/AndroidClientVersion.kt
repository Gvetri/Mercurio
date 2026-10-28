package org.mercurio.client.feature.entry.wiring

import android.content.Context
import org.mercurio.client.feature.entry.application.UNKNOWN_CLIENT_VERSION

/** Installed client version recorded in an export's metadata record. */
@Suppress("DEPRECATION")
fun androidClientVersion(context: Context): String =
    runCatching {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
    }.getOrNull().orEmpty().takeIf(String::isNotBlank) ?: UNKNOWN_CLIENT_VERSION
