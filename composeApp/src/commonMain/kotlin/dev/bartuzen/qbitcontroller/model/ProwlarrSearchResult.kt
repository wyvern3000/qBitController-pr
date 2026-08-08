package dev.bartuzen.qbitcontroller.model

import kotlinx.serialization.Serializable

/**
 * Maps to a single item in Prowlarr's `GET /api/v1/search` response array. Most fields are
 * nullable with defaults, since this schema is assembled from third-party documentation/SDKs
 * rather than a first-party spec (see docs/prowlarr-integration-plan.md, "待确认事项") - only
 * [guid] and [title] are treated as always present.
 */
@Serializable
data class ProwlarrSearchResult(
    val guid: String,
    val title: String,
    val fileName: String? = null,
    val size: Long? = null,
    val indexerId: Int? = null,
    val indexer: String? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    // "torrent" or "usenet". qBittorrent can only act on torrents, so callers should filter out
    // anything else - see ProwlarrSearchRepository.search().
    val protocol: String? = null,
    val downloadUrl: String? = null,
    val magnetUrl: String? = null,
    val infoUrl: String? = null,
)

/**
 * Maps a Prowlarr result onto the app's existing [Search.Result] model so it can flow through the
 * same sorting/filtering/download UI used for qBittorrent's own search plugins, without any
 * changes to that UI. See docs/prowlarr-integration-plan.md, section 4.1.
 *
 * [Search.Result.fileUrl] prefers [downloadUrl], then [magnetUrl], then [infoUrl] as a last
 * resort - this mirrors the priority Prowlarr itself uses internally when it needs a single link
 * for a release. Whichever one is picked is handed as-is to qBittorrent's `torrents/add`, which
 * resolves magnet links and fetches .torrent files from an HTTP(S) URL on its own, so no
 * magnet-vs-file branching is needed on this side.
 */
fun ProwlarrSearchResult.toSearchResult() = Search.Result(
    descriptionLink = infoUrl ?: "",
    fileName = fileName?.takeIf { it.isNotBlank() } ?: title,
    fileSize = size,
    fileUrl = downloadUrl ?: magnetUrl ?: infoUrl ?: "",
    leechers = leechers,
    seeders = seeders,
    siteUrl = indexer ?: "",
)
