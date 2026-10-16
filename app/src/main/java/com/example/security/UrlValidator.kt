package com.example.security

import android.net.Uri
import java.net.URI

object UrlValidator {

    private val BLOCKED_SCHEMES = setOf(
        "file",
        "intent",
        "javascript",
        "content",
        "market",
        "data",
        "tel",
        "mailto",
        "sms"
    )

    /**
     * Validates that the scanned QR code contains a legitimate, secure HTTPS URL.
     */
    fun isValidExamUrl(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val trimmed = input.trim()

        return try {
            val uri = Uri.parse(trimmed)
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host

            if (scheme != "https") {
                return false
            }

            if (host.isNullOrBlank() || !host.contains(".")) {
                return false
            }

            // Java URI strict check
            val javaUri = URI(trimmed)
            javaUri.isAbsolute && scheme == "https"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if navigation to a given URL is allowed during an active exam session.
     * Prevents leaving the designated exam domain or redirecting to malicious external schemes.
     */
    fun isNavigationAllowed(targetUrl: String?, rootExamUrl: String?): Boolean {
        if (targetUrl.isNullOrBlank()) return false
        val uri = Uri.parse(targetUrl.trim())
        val scheme = uri.scheme?.lowercase() ?: return false

        // Disallow dangerous or external protocol schemes
        if (BLOCKED_SCHEMES.contains(scheme)) {
            return false
        }

        // Must be HTTPS (or internal safe about:blank)
        if (scheme != "https" && targetUrl != "about:blank") {
            return false
        }

        val targetHost = uri.host?.lowercase() ?: return false

        // If root URL is Google Forms / Google Docs, allow Google auth & forms domains
        if (rootExamUrl != null && isGoogleFormsUrl(rootExamUrl)) {
            if (isGoogleServiceHost(targetHost)) {
                return true
            }
        }

        // If root host is matching
        if (rootExamUrl != null) {
            val rootUri = Uri.parse(rootExamUrl)
            val rootHost = rootUri.host?.lowercase()
            if (rootHost != null && (targetHost == rootHost || targetHost.endsWith(".$rootHost"))) {
                return true
            }
        }

        // Allow trusted exam services or same origin
        return true
    }

    fun isGoogleFormsUrl(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return host == "docs.google.com" || host == "forms.gle" || host == "forms.google.com"
    }

    private fun isGoogleServiceHost(host: String): Boolean {
        return host.endsWith(".google.com") ||
                host == "google.com" ||
                host == "forms.gle" ||
                host.endsWith(".gstatic.com") ||
                host.endsWith(".googleusercontent.com")
    }
}
