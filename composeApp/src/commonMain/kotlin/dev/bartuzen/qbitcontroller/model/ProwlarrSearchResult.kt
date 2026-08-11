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
    // Torznab/Newznab categories for this specific result. Confirmed present in the real API
    // response (docs/prowlarr-integration-plan.md section 2), but the assumption in
    // docs/prowlarr-download-defaults-plan.md that this was a flat List<Int> was wrong - a real
    // device search (round 13) hit a JsonConvertException, "Expected numeric literal at path:
    // $[0].categories[0]", because the actual shape is a list of *objects* (`{"id": 3000, ...}`),
    // matching ProwlarrIndexerCapabilities.categories/ProwlarrCategory, not bare ids. Reusing
    // ProwlarrCategory here rather than a separate type - ignoreUnknownKeys is on globally
    // (RequestManager.json), so any extra fields on the result-level category object beyond
    // id/name/subCategories are simply dropped, no new model needed.
    val categories: List<ProwlarrCategory>? = null,
)

/**
 * Maps a Prowlarr result onto the app's existing [Search.Result] model so it can flow through the
 * same sorting/filtering/download UI used for qBittorrent's own search plugins, without any
 * changes to that UI. See docs/prowlarr-integration-plan.md, section 4.1.
 *
 * [Search.Result.fileUrl] prefers [downloadUrl], then [magnetUrl]. Unlike the original plan (which
 * assumed the URL would be handed to qBittorrent as-is and fetched server-side), round 3 has the
 * client fetch [downloadUrl] itself and upload the bytes, and pass [magnetUrl] straight through as
 * a link - see ProwlarrSearchViewModel.addTorrent(). [infoUrl] is deliberately not used as a
 * fallback here even though it sometimes is populated: it points at a results/info page, not a
 * downloadable file, so fetching it would just return HTML. It's still exposed as
 * [Search.Result.descriptionLink] for display purposes.
 */
fun ProwlarrSearchResult.toSearchResult() = Search.Result(
    descriptionLink = infoUrl ?: "",
    fileName = fileName?.takeIf { it.isNotBlank() } ?: title,
    fileSize = size,
    fileUrl = downloadUrl ?: magnetUrl ?: "",
    leechers = leechers,
    seeders = seeders,
    siteUrl = indexer ?: "",
    categories = categories?.map { it.id } ?: emptyList(),
)
