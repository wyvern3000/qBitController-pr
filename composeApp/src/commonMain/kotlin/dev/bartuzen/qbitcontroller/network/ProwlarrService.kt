package dev.bartuzen.qbitcontroller.network

import dev.bartuzen.qbitcontroller.model.ProwlarrSystemStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.http.appendEncodedPathSegments
import io.ktor.http.takeFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around Prowlarr's REST API (`/api/v1/...`), following the same [Response]-returning
 * convention as [TorrentService]. Round 1 only needs [getSystemStatus] for the "Test Connection"
 * flow in the Prowlarr settings screen; search endpoints will be added once the search-source
 * integration lands (see docs/prowlarr-integration-plan.md).
 */
class ProwlarrService(
    val client: HttpClient,
    val baseUrl: String,
) {
    suspend inline fun <reified T> get(path: String, parameters: Map<String, Any?> = emptyMap()): Response<T> =
        client.prepareGet {
            url.takeFrom(baseUrl).appendEncodedPathSegments("api/v1/$path")
            parameters.forEach { (key, value) ->
                if (value != null) {
                    url.parameters.append(key, value.toString())
                }
            }
        }.execute(::execute)

    suspend inline fun <reified T> execute(
        response: HttpResponse,
        noinline body: suspend () -> T? = { response.body<T>() },
    ): Response<T> = withContext(Dispatchers.Default) {
        val code = response.status.value
        val body = if (code in 200..<300 && code != 204 && code != 205) body() else null

        Response(code, body)
    }

    suspend fun getSystemStatus(): Response<ProwlarrSystemStatus> = get("system/status")
}
