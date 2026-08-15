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
    // $[0].categories[0]", because the actual shape is a list of *objects* (`{"id": 3000, ...}`).
    // The first fix reused ProwlarrCategory (same shape as ProwlarrIndexerCapabilities.categories),
    // but a second real device test immediately hit a *different* JsonConvertException on the same
    // field, "Field 'name' is required ... missing at path: $[0].categories[1]" - unlike capabilities
    // categories (confirmed to always carry name in round 7), individual search-result category
    // entries can apparently omit name entirely. Since toSearchResult() below only ever extracts
    // [id], this now uses the dedicated minimal [ProwlarrResultCategory] instead of ProwlarrCategory
    // - declaring only id sidesteps any future surprises about which other fields are/aren't always
    // present on this specific endpoint's category objects (ignoreUnknownKeys drops the rest).
    val categories: List<ProwlarrResultCategory>? = null,
    // Torznab/Newznab "indexer flags" (site-specific promo tags like "freeleech"/"halfleech" -
    // see qbitcontroller.composeapp.generated.resources.prowlarr_search_filter_flags usage in
    // ProwlarrSearchScreen.kt). UNVERIFIED against a real API response - unlike [categories] above
    // (which got its shape wrong twice against real search results, round 13), this field hasn't
    // been checked against a real device at all yet. Declared as nullable/optional specifically so
    // a wrong assumption here degrades to "flags missing" instead of a JsonConvertException taking
    // out the whole search - see docs/prowlarr-p2-feedback-round1-plan.md section 4 and the
    // PROGRESS.md "待验证" list.
    val indexerFlags: List<String>? = null,
)

/**
 * Torznab/Newznab category as it appears nested inside an individual `/api/v1/search` result item
 * (see [ProwlarrSearchResult.categories]). Deliberately *not* the same type as [ProwlarrCategory]
 * (which models `/api/v1/indexer`'s `capabilities.categories`) - the two endpoints don't return the
 * same shape, and round 13 confirmed via two separate real-device JsonConvertExceptions that this
 * endpoint's category objects can't be relied on to carry anything beyond [id].
 */
@Serializable
data class ProwlarrResultCategory(
    val id: Int,
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
    indexerFlags = indexerFlags ?: emptyList(),
    indexerId = indexerId,
)
