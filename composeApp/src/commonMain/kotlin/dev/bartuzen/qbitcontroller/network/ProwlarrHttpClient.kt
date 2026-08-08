package dev.bartuzen.qbitcontroller.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * Builds a standalone [HttpClient] for talking to a Prowlarr instance.
 *
 * This is intentionally separate from [createHttpClient], which is tied to [dev.bartuzen.qbitcontroller.model.ServerConfig]
 * (cookie-based qBittorrent session, Basic Auth, custom headers, etc.). Prowlarr uses simple
 * `X-Api-Key` header authentication and is not associated with any particular qBittorrent server,
 * so it gets its own lightweight client instead of overloading [createHttpClient]'s signature.
 *
 * Only self-signed certificate trust is supported for now (DNS over HTTPS support may be added
 * later alongside [createHttpClient] if there is demand for it).
 */
expect fun createProwlarrHttpClient(
    trustSelfSignedCertificates: Boolean,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient
