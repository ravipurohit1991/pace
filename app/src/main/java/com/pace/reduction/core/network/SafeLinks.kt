package com.pace.reduction.core.network

import android.net.Uri
import java.net.URI

object SafeLinks {
    private val allowedHosts = setOf(
        "open-meteo.com",
        "smokefree.gov",
        "www.cdc.gov",
        "ollama.com",
        "docs.ollama.com",
        "gabrielecirulli.github.io",
        "www.chiark.greenend.org.uk",
    )

    fun isAllowed(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && uri.host?.lowercase() in allowedHosts &&
            uri.userInfo == null
    }

    fun requireAllowed(url: String): Uri {
        require(isAllowed(url)) { "URL is not on the Pace allowlist" }
        return Uri.parse(url)
    }
}
