package dev.bartuzen.qbitcontroller.model

import kotlinx.serialization.Serializable

@Serializable
data class ProwlarrConfig(
    val url: String = "",
    val apiKey: String = "",
    val trustSelfSignedCertificates: Boolean = false,
) {
    val requestUrl = buildString {
        if (!url.contains("://")) {
            append("http://")
        }

        append(url)

        if (!url.endsWith("/")) {
            append("/")
        }
    }

    val isConfigured = url.isNotBlank() && apiKey.isNotBlank()
}
