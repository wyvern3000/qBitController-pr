package dev.bartuzen.qbitcontroller.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

// trustSelfSignedCertificates is intentionally ignored on iOS, matching createHttpClient's existing
// behavior (see supportsSelfSignedCertificates() == false on this platform).
actual fun createProwlarrHttpClient(
    trustSelfSignedCertificates: Boolean,
    block: HttpClientConfig<*>.() -> Unit,
) = HttpClient(Darwin) {
    block()
}
