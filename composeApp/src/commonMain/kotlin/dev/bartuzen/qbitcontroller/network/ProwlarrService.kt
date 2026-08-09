package dev.bartuzen.qbitcontroller.network

import dev.bartuzen.qbitcontroller.model.ProwlarrIndexer
import dev.bartuzen.qbitcontroller.model.ProwlarrSearchResult
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
 * convention as [TorrentService].
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

    suspend fun getIndexers(): Response<List<ProwlarrIndexer>> = get("indexer")

    // Used for fetching a .torrent file's raw bytes directly from the client (phone/desktop)
    // rather than requiring the qBittorrent server to be able to reach Prowlarr - see
    // ProwlarrSearchRepository.downloadTorrentFile() and docs/prowlarr-integration-plan.md
    // section 4.7. url is expected to be an absolute URL (Prowlarr's own downloadUrl), not a
    // path relative to baseUrl.
    suspend fun downloadFile(url: String): Response<ByteArray> = client.prepareGet(url).execute(::execute)

    // indexerIds is a repeated query parameter (?indexerIds=1&indexerIds=2&...), which the generic
    // get(path, parameters: Map<String, Any?>) helper above can't express (one value per key), so
    // this is built directly instead of going through it.
    suspend fun search(query: String, indexerIds: List<Int>? = null): Response<List<ProwlarrSearchResult>> =
        client.prepareGet {
            url.takeFrom(baseUrl).appendEncodedPathSegments("api/v1/search")
            url.parameters.append("query", query)
            url.parameters.append("type", "search")
            indexerIds?.forEach { id ->
                url.parameters.append("indexerIds", id.toString())
            }
        }.execute(::execute)
}

