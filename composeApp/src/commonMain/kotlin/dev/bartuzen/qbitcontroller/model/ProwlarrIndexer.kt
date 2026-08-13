package dev.bartuzen.qbitcontroller.model

import kotlinx.serialization.Serializable

/**
 * Maps to a single item in Prowlarr's `GET /api/v1/indexer` response array. Field names were
 * originally guessed from third-party docs (docs/prowlarr-p1-search-ui-and-tabs-plan.md, section
 * 2.1) and confirmed against a real, redacted response sample in round 7 - [id]/[name]/[enable]
 * matched as guessed, but [capabilities.categories][ProwlarrIndexerCapabilities.categories] turned
 * out to be recursive (see [ProwlarrCategory]), which the original guess didn't account for.
 */
@Serializable
data class ProwlarrIndexer(
    val id: Int,
    val name: String,
    val enable: Boolean = true,
    // "torrent" or "usenet" (see ProwlarrSearchResult.protocol, which is filtered on downstream).
    // Not used to filter the indexer picker itself yet - kept here in case a later round wants to
    // grey out/hide usenet-only indexers in this list too, not just their search results.
    val protocol: String? = null,
    val capabilities: ProwlarrIndexerCapabilities? = null,
)

@Serializable
data class ProwlarrIndexerCapabilities(
    val categories: List<ProwlarrCategory> = emptyList(),
)

/**
 * A Torznab category. Confirmed recursive against a real response (round 7): e.g. "TV" (id 5000)
 * has "TV/Anime" (id 5070) nested inside its own [subCategories], rather than being listed
 * separately - the plan's original guess (flat `id`/`name` only) missed this. In practice Prowlarr
 * only nests one level deep, but this is modeled as arbitrarily nestable to match the real shape
 * rather than assume a fixed depth.
 */
@Serializable
data class ProwlarrCategory(
    val id: Int,
    val name: String,
    val subCategories: List<ProwlarrCategory> = emptyList(),
)
