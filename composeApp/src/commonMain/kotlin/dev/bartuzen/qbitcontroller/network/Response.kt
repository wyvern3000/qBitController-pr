package dev.bartuzen.qbitcontroller.network

/**
 * [errorMessage] is an optional, best-effort human-readable detail extracted from the response
 * body when [code] is outside the 2xx range (see [ProwlarrService.execute] - the only caller that
 * currently populates it). Defaults to null so every existing `Response(code, body)` call site
 * (TorrentService, RequestManager) keeps compiling/behaving unchanged; qBittorrent-side errors
 * simply never populate this field.
 */
class Response<T>(
    val code: Int,
    val body: T?,
    val errorMessage: String? = null,
)
