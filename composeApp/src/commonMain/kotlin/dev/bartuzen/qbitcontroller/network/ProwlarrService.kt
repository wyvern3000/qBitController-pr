package dev.bartuzen.qbitcontroller.network

import dev.bartuzen.qbitcontroller.model.ProwlarrIndexer
import dev.bartuzen.qbitcontroller.model.ProwlarrSearchResult
import dev.bartuzen.qbitcontroller.model.ProwlarrSystemStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.appendEncodedPathSegments
import io.ktor.http.takeFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

        // On failure, best-effort capture whatever Prowlarr (or, for downloadFile(), the
        // underlying indexer/tracker that Prowlarr proxies to) sent back, instead of discarding
        // the body entirely - see Response.errorMessage KDoc. This is the piece that was missing
        // before: every Prowlarr-side error surfaced to the user as a bare "API returned error
        // <code>" with no way to tell "search query rejected" apart from "this specific release's
        // download link failed on Prowlarr's/indexer's side" (the latter being a well-documented,
        // usually server-side Prowlarr/indexer issue - expired indexer cookie, removed release,
        // reverse-proxy URL rewriting, etc. - not something this client can fix, but at least now
        // visible). Every exception here is deliberately swallowed: a body isn't guaranteed to be
        // readable as text, and a parsing failure here must never mask the real error (the
        // numeric code) or crash the call.
        val errorMessage = if (body == null && code !in 200..<300) {
            runCatching { response.bodyAsText() }.getOrNull()?.let(::extractErrorMessage)
        } else {
            null
        }

        Response(code, body, errorMessage)
    }

    suspend fun getSystemStatus(): Response<ProwlarrSystemStatus> = get("system/status")

    suspend fun getIndexers(): Response<List<ProwlarrIndexer>> = get("indexer")

    // Used for fetching a .torrent file's raw bytes directly from the client (phone/desktop)
    // rather than requiring the qBittorrent server to be able to reach Prowlarr - see
    // ProwlarrSearchRepository.downloadTorrentFile() and docs/prowlarr-integration-plan.md
    // section 4.7. url is expected to be an absolute URL (Prowlarr's own downloadUrl), not a
    // path relative to baseUrl.
    suspend fun downloadFile(url: String): Response<ByteArray> = client.prepareGet(url).execute(::execute)

    // indexerIds/categories are repeated query parameters (?indexerIds=1&indexerIds=2&...,
    // ?categories=2000&categories=2060&...), which the generic get(path, parameters: Map<String,
    // Any?>) helper above can't express (one value per key), so this is built directly instead of
    // going through it. Prowlarr doesn't distinguish top-level vs sub-category ids here - both are
    // just entries in the same categories list (see ProwlarrSearchScreen's category picker, which
    // sends both together).
    suspend fun search(
        query: String,
        indexerIds: List<Int>? = null,
        categories: List<Int>? = null,
    ): Response<List<ProwlarrSearchResult>> =
        client.prepareGet {
            url.takeFrom(baseUrl).appendEncodedPathSegments("api/v1/search")
            url.parameters.append("query", query)
            url.parameters.append("type", "search")
            indexerIds?.forEach { id ->
                url.parameters.append("indexerIds", id.toString())
            }
            categories?.forEach { id ->
                url.parameters.append("categories", id.toString())
            }
        }.execute(::execute)
}

// Cap applied below to whatever's extracted from an error body, so a pathological/huge response
// (some indexer's error page, a stray stack trace, etc.) can't blow up a snackbar.
private const val MAX_ERROR_MESSAGE_LENGTH = 200

/**
 * Pulls a human-readable detail out of a non-2xx response body, for [ProwlarrService.execute].
 * Prowlarr's own JSON error responses are typically a small object with a `message` field (e.g.
 * `{"message":"Failed to normalize provided link"}`) - `error`/`description` are checked too since
 * not every failure path in Prowlarr uses the same key, and its ASP.NET-style 400 validation
 * errors come back as an array of `{"errorMessage": "...", ...}` objects instead of a single
 * object, so the first element is checked the same way when the root is an array. Falls back to
 * the raw trimmed text if none of that applies and it doesn't look like an HTML error page, since
 * a raw `<html>...` dump isn't useful to show and could be arbitrarily large. Returns null - rather
 * than an empty string - when nothing usable was found, so callers can fall back to the plain
 * "API returned error <code>" message instead of appending nothing.
 */
fun extractErrorMessage(rawBody: String): String? {
    val trimmed = rawBody.trim()
    if (trimmed.isEmpty()) {
        return null
    }

    fun JsonObject.firstMessage() =
        (this["message"] ?: this["error"] ?: this["errorMessage"] ?: this["description"])
            ?.let { it as? JsonPrimitive }
            ?.takeIf { it.isString }
            ?.content

    val fromJson = runCatching {
        when (val element = Json.parseToJsonElement(trimmed)) {
            is JsonObject -> element.firstMessage()
            is JsonArray -> (element.firstOrNull() as? JsonObject)?.firstMessage()
            else -> null
        }
    }.getOrNull()

    val candidate = fromJson ?: trimmed.takeUnless { it.startsWith("<") }

    return candidate?.takeIf { it.isNotBlank() }?.take(MAX_ERROR_MESSAGE_LENGTH)
}

